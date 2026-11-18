package com.processpuzzle.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpModelListerTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String body = "{}";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void theModelNamesAreSortedWithoutDuplicatesAndTheKeyIsSentAsBearer() {
        body = "{\"data\":[{\"id\":\"b-model\"},{\"id\":\"a-model\"},{\"id\":\"b-model\"},{\"id\":\" \"},{}]}";

        assertThat(new HttpModelLister().list(baseUrl, "sk-secret")).containsExactly("a-model", "b-model");
        assertThat(authorization.get()).isEqualTo("Bearer sk-secret");
    }

    @Test
    void aRejectedKeyIsSaidSo() {
        status = 401;

        assertThatThrownBy(() -> new HttpModelLister().list(baseUrl, "bad")).isInstanceOf(ModelLister.ModelListException.class)
            .hasMessageContaining("rejected the API key");
    }

    @Test
    void anyOtherFailureNamesTheStatus() {
        status = 500;

        assertThatThrownBy(() -> new HttpModelLister().list(baseUrl, "k")).hasMessageContaining("500");
    }

    @Test
    void anAnswerWithoutModelsIsAFailure() {
        body = "{\"data\":[]}";

        assertThatThrownBy(() -> new HttpModelLister().list(baseUrl, "k")).hasMessageContaining("no models");
    }

    @Test
    void anUnreachableProviderIsAFailure() {
        server.stop(0);

        assertThatThrownBy(() -> new HttpModelLister().list(baseUrl, "k")).hasMessageContaining("could not be reached");
    }
}
