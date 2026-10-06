package com.processpuzzle.starter.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.starter.StarterProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** Against a real HTTP server standing in for the public bucket. */
class HttpStarterRegistryTest {

    private static final String CATALOG = """
            {
              "catalogVersion": 1,
              "starters": [{
                "id": "inventory", "name": "Inventory", "license": "Apache-2.0", "screenshots": ["ignored"],
                "versions": [{ "version": "1.0.0", "publishedAt": "2026-10-06", "sha256": "abc",
                               "bundle": "inventory/1.0.0/bundle.zip" }]
              }]
            }
            """;

    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final AtomicInteger catalogReads = new AtomicInteger();
    private HttpServer server;
    private StarterProperties properties;
    private MutableClock clock;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/processpuzzle-starters/", exchange -> {
            String path = exchange.getRequestURI().getPath().substring("/processpuzzle-starters/".length());
            if (path.equals(HttpStarterRegistry.CATALOG)) {
                catalogReads.incrementAndGet();
            }
            byte[] body = files.get(path);
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        });
        server.start();
        files.put("catalog.json", CATALOG.getBytes(StandardCharsets.UTF_8));
        files.put("inventory/1.0.0/bundle.zip", new byte[] {1, 2, 3});

        properties = new StarterProperties();
        properties.getRegistry().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/processpuzzle-starters/");
        clock = new MutableClock(Instant.parse("2026-10-06T10:00:00Z"));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private HttpStarterRegistry registry() {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("clock", clock);
        ObjectProvider<Clock> provider = factory.getBeanProvider(Clock.class);
        return new HttpStarterRegistry(properties, provider);
    }

    @Test
    void readsTheCatalogIgnoringFieldsItDoesNotKnow() {
        StarterCatalog.Starter starter = registry().catalog().starter("inventory").orElseThrow();

        assertThat(starter.license()).isEqualTo("Apache-2.0");
        assertThat(starter.version("1.0.0").orElseThrow().effectiveStatus()).isEqualTo(StarterCatalog.Version.PUBLISHED);
    }

    @Test
    void reusesTheCatalogUntilItsTtlRunsOut() {
        HttpStarterRegistry registry = registry();
        registry.catalog();
        registry.catalog();
        assertThat(catalogReads).hasValue(1);

        clock.advance(properties.getRegistry().getCatalogTtl().plusSeconds(1));
        registry.catalog();
        assertThat(catalogReads).hasValue(2);
    }

    @Test
    void downloadsABundle() {
        assertThat(registry().bundle("inventory/1.0.0/bundle.zip")).containsExactly(1, 2, 3);
    }

    @Test
    void anHttpErrorOrUnparseableCatalogIsUnavailable() {
        assertThatThrownBy(() -> registry().bundle("inventory/9.9.9/bundle.zip"))
                .isInstanceOf(RegistryUnavailableException.class).hasMessageContaining("answered 404");

        files.put("catalog.json", "not json".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> registry().catalog())
                .isInstanceOf(RegistryUnavailableException.class).hasMessageContaining("not valid");
    }

    @Test
    void refusesUnsafePathsAndOversizedDownloads() {
        assertThatThrownBy(() -> registry().bundle("../secrets"))
                .isInstanceOf(RegistryUnavailableException.class).hasMessageContaining("unsafe path");
        assertThatThrownBy(() -> registry().bundle("http://elsewhere/x.zip"))
                .isInstanceOf(RegistryUnavailableException.class).hasMessageContaining("unsafe path");

        properties.getRegistry().setMaxDownloadBytes(2);
        assertThatThrownBy(() -> registry().bundle("inventory/1.0.0/bundle.zip"))
                .isInstanceOf(RegistryUnavailableException.class).hasMessageContaining("larger than 2 bytes");
    }

    @Test
    void withoutABaseUrlTheCatalogIsEmptyAndNothingCanBeDownloaded() {
        properties.getRegistry().setBaseUrl(" ");

        assertThat(registry().catalog().starters()).isEmpty();
        assertThatThrownBy(() -> registry().bundle("inventory/1.0.0/bundle.zip"))
                .isInstanceOf(RegistryUnavailableException.class).hasMessageContaining("No registry is configured");
    }

    @Test
    void anUnreachableRegistryIsUnavailable() {
        properties.getRegistry().setBaseUrl("http://127.0.0.1:1");
        properties.getRegistry().setConnectTimeout(Duration.ofMillis(500));

        assertThatThrownBy(() -> registry().catalog())
                .isInstanceOf(RegistryUnavailableException.class).hasMessageContaining("unreachable");
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
