package com.processpuzzle.starter.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.processpuzzle.starter.StarterProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Reads the registry over plain HTTP: {@code <base-url>/catalog.json} and the bundles it names. The
 * bucket is public-read by design, so there is no credential and no S3 client, and any static file
 * host would serve as well.
 *
 * <p>The catalog is cached for {@code catalog-ttl}; bundles are not, they are fetched once per install.
 * A blank {@code base-url} means this deployment has no registry, and the catalog is empty.
 *
 * <p>So does a registry that answers 404 for {@code catalog.json}: that is a bucket CI has not
 * published to yet, and the publish workflow reads the same 404 the same way. Only the catalog gets
 * that reading. A 404 for a bundle the catalog names is still a broken registry.
 */
@Component
public class HttpStarterRegistry implements StarterRegistry {

    public static final String CATALOG_FILE = "catalog.json";

    /** Relative, no traversal: the catalog is CI's, but the bucket it sits in is world-readable. */
    private static final Pattern SAFE_PATH =
            Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]*+(?:/[A-Za-z0-9_-][A-Za-z0-9._-]*+)*+");

    private final StarterProperties.Registry settings;
    private final HttpClient client;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();

    private StarterCatalog cached;
    private Instant cachedUntil = Instant.MIN;

    public HttpStarterRegistry(StarterProperties properties, ObjectProvider<Clock> clock) {
        this.settings = properties.getRegistry();
        this.client = HttpClient.newBuilder()
                .connectTimeout(settings.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    @Override
    public synchronized StarterCatalog catalog() {
        if (isDisabled()) {
            return StarterCatalog.EMPTY;
        }
        Instant now = clock.instant();
        if (cached == null || now.isAfter(cachedUntil)) {
            Optional<byte[]> content = get(CATALOG_FILE, true);
            try {
                StarterCatalog catalog = content.isEmpty() ? null : json.readValue(content.orElseThrow(), StarterCatalog.class);
                cached = catalog == null ? StarterCatalog.EMPTY : catalog;
            } catch (IOException e) {
                throw new RegistryUnavailableException("The registry's " + CATALOG_FILE + " is not valid: " + e.getMessage(), e);
            }
            cachedUntil = now.plus(settings.getCatalogTtl());
        }
        return cached;
    }

    @Override
    public byte[] bundle(String path) {
        if (isDisabled()) {
            throw new RegistryUnavailableException("No registry is configured (base-starter.registry.base-url).");
        }
        return get(path, false).orElseThrow();
    }

    /** @param missingIsEmpty whether a 404 answers {@link Optional#empty()} rather than failing */
    private Optional<byte[]> get(String path, boolean missingIsEmpty) {
        if (path == null || !SAFE_PATH.matcher(path).matches() || path.contains("..")) {
            throw new RegistryUnavailableException("The registry names an unsafe path: '" + path + "'.");
        }
        URI uri = URI.create(baseUrl() + "/" + path);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(settings.getRequestTimeout()).GET().build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (missingIsEmpty && response.statusCode() == 404) {
                    return Optional.empty();
                }
                if (response.statusCode() != 200) {
                    throw new RegistryUnavailableException("The registry answered " + response.statusCode() + " for " + uri + ".");
                }
                return Optional.of(readCapped(body, uri));
            }
        } catch (IOException e) {
            throw new RegistryUnavailableException("The registry is unreachable at " + uri + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RegistryUnavailableException("Interrupted while reading " + uri + ".", e);
        }
    }

    private byte[] readCapped(InputStream body, URI uri) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long size = 0;
        int read;
        while ((read = body.read(buffer)) != -1) {
            size += read;
            if (size > settings.getMaxDownloadBytes()) {
                throw new RegistryUnavailableException(uri + " is larger than " + settings.getMaxDownloadBytes() + " bytes.");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private boolean isDisabled() {
        return settings.getBaseUrl() == null || settings.getBaseUrl().isBlank();
    }

    private String baseUrl() {
        String base = settings.getBaseUrl().trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
