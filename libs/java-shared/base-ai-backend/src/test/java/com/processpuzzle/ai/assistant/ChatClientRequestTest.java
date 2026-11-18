package com.processpuzzle.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** What the streamed chat request looks like on the wire, per provider. */
class ChatClientRequestTest {

    private static final String THINKING_CHUNKS =
        "data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"m\",\"choices\":[{\"index\":0,"
        + "\"delta\":{\"role\":\"assistant\",\"content\":[{\"type\":\"thinking\",\"thinking\":[{\"type\":\"text\",\"text\":\"hmm\"}],"
        + "\"closed\":true}]},\"finish_reason\":null}]}\n\n"
        + "data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"m\",\"choices\":[{\"index\":0,"
        + "\"delta\":{\"content\":[{\"type\":\"text\",\"text\":\"Hello\"},{\"type\":\"text\",\"text\":\" there\"}]},"
        + "\"finish_reason\":\"stop\"}]}\n\n"
        + "data: [DONE]\n\n";

    private static final String CHUNK = "data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"m\","
        + "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Hi\"},\"finish_reason\":\"stop\"}]}\n\n"
        + "data: [DONE]\n\n";

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> requestBody = new AtomicReference<>("");
    private volatile String answer = CHUNK;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void theStreamAsksForTheUsageAndNothingMistralRefuses() {
        OrganizationAiSubscriptions.chatClient(baseUrl, "sk-test", "m")
            .prompt().user("hello").stream().content().blockLast();

        assertThat(requestBody.get()).contains("\"stream\":true").contains("\"include_usage\":true")
            .doesNotContain("include_obfuscation");
    }

    @Test
    void anAnswerMadeOfTypedPartsIsReadAsItsTextWithoutTheReasoning() {
        answer = THINKING_CHUNKS;

        String reply = OrganizationAiSubscriptions.chatClient(baseUrl, "sk-test", "m")
            .prompt().user("hello").stream().content().collectList().block().stream().reduce("", String::concat);

        assertThat(reply).isEqualTo("Hello there");
    }
}
