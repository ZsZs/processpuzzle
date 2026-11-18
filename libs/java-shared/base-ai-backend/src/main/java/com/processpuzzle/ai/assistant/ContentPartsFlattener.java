package com.processpuzzle.ai.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import okhttp3.Interceptor;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Okio;

/**
 * Makes a provider's answer readable by the OpenAI client. Mistral's newer models send a message's {@code content} as
 * a list of typed parts, for example {@code [{"type":"thinking",...},{"type":"text","text":"Hi"}]}, where the OpenAI
 * protocol has a string, and the client refuses the answer. This keeps the text parts, joined, and drops the rest (the
 * model's reasoning is not shown). Answers whose content is already a string pass through byte for byte.
 *
 * <p>Streamed answers are rewritten line by line as they arrive, so the reply still streams.
 */
public class ContentPartsFlattener implements Interceptor {

    private static final String MARKER = "\"content\":[";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        ResponseBody body = response.body();
        if (body == null || body.contentType() == null || !"text".equals(body.contentType().type())
            || !"event-stream".equals(body.contentType().subtype())) {
            return response;
        }
        InputStream flattened = new LineRewritingStream(body.byteStream());
        return response.newBuilder()
            .body(ResponseBody.create(Okio.buffer(Okio.source(flattened)), body.contentType(), -1))
            .build();
    }

    /** Rewrites one server-sent {@code data:} line; any other line, or one it cannot read, is returned as it is. */
    static String flatten(String line) {
        if (!line.startsWith("data:") || !line.contains(MARKER)) {
            return line;
        }
        try {
            JsonNode root = MAPPER.readTree(line.substring("data:".length()).trim());
            boolean changed = false;
            for (JsonNode choice : root.path("choices")) {
                for (String holder : new String[] {"delta", "message"}) {
                    JsonNode content = choice.path(holder).path("content");
                    if (content.isArray()) {
                        ((ObjectNode) choice.get(holder)).put("content", textOf(content));
                        changed = true;
                    }
                }
            }
            return changed ? "data: " + MAPPER.writeValueAsString(root) : line;
        } catch (IOException | RuntimeException e) {
            return line;
        }
    }

    private static String textOf(JsonNode parts) {
        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            if (part.isTextual()) {
                text.append(part.asText());
            } else if ("text".equals(part.path("type").asText()) && part.path("text").isTextual()) {
                text.append(part.path("text").asText());
            }
        }
        return text.toString();
    }

    /** Reads the source a line at a time, hands on each rewritten, and ends when the source does. */
    private static final class LineRewritingStream extends InputStream {

        private final InputStream source;
        private InputStream current = new ByteArrayInputStream(new byte[0]);
        private boolean ended;

        LineRewritingStream(InputStream source) {
            this.source = source;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) == -1 ? -1 : one[0] & 0xFF;
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            while (!ended) {
                int count = current.read(target, offset, length);
                if (count > 0) {
                    return count;
                }
                String line = readLine();
                if (line == null) {
                    ended = true;
                } else {
                    current = new ByteArrayInputStream((flatten(line) + "\n").getBytes(StandardCharsets.UTF_8));
                }
            }
            return current.read(target, offset, length);
        }

        private String readLine() throws IOException {
            java.io.ByteArrayOutputStream line = new java.io.ByteArrayOutputStream();
            int next = source.read();
            if (next == -1) {
                return null;
            }
            while (next != -1 && next != '\n') {
                line.write(next);
                next = source.read();
            }
            String text = line.toString(StandardCharsets.UTF_8);
            return text.endsWith("\r") ? text.substring(0, text.length() - 1) : text;
        }

        @Override
        public void close() throws IOException {
            source.close();
        }
    }
}
