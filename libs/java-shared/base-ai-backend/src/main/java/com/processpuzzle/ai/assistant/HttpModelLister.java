package com.processpuzzle.ai.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.TreeSet;

/**
 * Lists a service's models with {@code GET <baseUrl>/models}, which Mistral, OpenAI and the services that copy them
 * answer with {@code {"data":[{"id":"..."}]}}. Redirects are not followed, so a public address cannot hand the call on
 * to an internal one, and the answer is read up to a limit.
 */
public class HttpModelLister implements ModelLister {

    private static final int MAX_BYTES = 1_000_000;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public List<String> list(String baseUrl, String apiKey) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/+$", "") + "/models"))
            .timeout(TIMEOUT).header("Authorization", "Bearer " + apiKey).header("Accept", "application/json").GET().build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                int status = response.statusCode();
                if (status == 401 || status == 403) {
                    throw new ModelListException("The provider rejected the API key");
                }
                if (status != 200) {
                    throw new ModelListException("The provider answered %d when asked for its models".formatted(status));
                }
                return namesOf(mapper.readTree(body.readNBytes(MAX_BYTES)));
            }
        } catch (IOException e) {
            throw new ModelListException("The provider could not be reached", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelListException("The request was interrupted", e);
        }
    }

    static List<String> namesOf(JsonNode answer) {
        TreeSet<String> names = new TreeSet<>();
        for (JsonNode model : answer.path("data")) {
            String id = model.path("id").asText("");
            if (!id.isBlank()) {
                names.add(id);
            }
        }
        if (names.isEmpty()) {
            throw new ModelListException("The provider listed no models");
        }
        return List.copyOf(names);
    }
}
