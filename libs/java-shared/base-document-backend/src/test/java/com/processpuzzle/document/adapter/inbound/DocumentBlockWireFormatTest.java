package com.processpuzzle.document.adapter.inbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.processpuzzle.document.domain.BlockKind;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.model.DocumentBlockInput;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A TEXT block's {@code content} across the JSON boundary — the one step no other test in this
 * module takes. {@code DocumentEndpointTest} calls the controller as a plain object and
 * {@code DocumentMapperTest} compares Java values, so nothing here ever serialized a block; that is
 * how {@code content} came to be typed as a Jackson 2 {@code JsonNode} in the generated models,
 * which the running application answered with {@code {"array":false,"bigDecimal":false,…}} and a
 * 500 on every write.
 *
 * <p>Serialized with Jackson 3 deliberately. That is what Spring Boot 4 binds HTTP with, and it is
 * the whole point: the Jackson 2 mapper the domain and the JSON columns use round-trips its own
 * node type perfectly, so a test written with it would have passed throughout the outage.
 */
class DocumentBlockWireFormatTest {

    /** Plain defaults, as close as a unit test gets to the converter behind an {@code @RestController}. */
    private static final JsonMapper WIRE = JsonMapper.builder().build();

    private static final String TIPTAP_JSON = """
            {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Hello"}]}]}""";

    private final DocumentMapper mapper = new DocumentMapper();

    @Test
    void aTextBlockGoesOutAsTheTiptapDocumentItself() {
        DocumentBlock block = new DocumentBlock("intro", BlockKind.TEXT, true, tiptapDocument(), null, null, null, null, null);

        String json = WIRE.writeValueAsString(mapper.toModel(block));

        assertThat(json).contains("\"content\":" + TIPTAP_JSON);
    }

    @Test
    void aTextBlockPostedAsJsonReachesTheDomainAsThatSameTree() {
        String body = """
                {"id":"intro","kind":"TEXT","editable":true,"content":%s}""".formatted(TIPTAP_JSON);

        DocumentBlockInput input = WIRE.readValue(body, DocumentBlockInput.class);
        DocumentBlock block = mapper.toBlock("intro", input);

        assertThat(block.content()).isEqualTo(tiptapDocument());
    }

    /**
     * A WIDGET block has no content, and must not acquire an empty one — the failure mode of describing
     * TiptapDocument as a free-form map, which makes openapi-generator initialize the field to
     * {@code new HashMap<>()} and turns "no content" into {@code {}} in every response.
     */
    @Test
    void aBlockWithoutContentCarriesNone() {
        DocumentBlock widget = new DocumentBlock("grid", BlockKind.WIDGET, null, null, null, "entity-grid", null, null, null);

        assertThat(mapper.toModel(widget).getContent()).isNull();
    }

    private static JsonNode tiptapDocument() {
        ObjectNode text = JsonNodeFactory.instance.objectNode().put("type", "text").put("text", "Hello");
        ObjectNode paragraph = JsonNodeFactory.instance.objectNode().put("type", "paragraph");
        paragraph.putArray("content").add(text);
        ObjectNode document = JsonNodeFactory.instance.objectNode().put("type", "doc");
        document.putArray("content").add(paragraph);
        return document;
    }
}
