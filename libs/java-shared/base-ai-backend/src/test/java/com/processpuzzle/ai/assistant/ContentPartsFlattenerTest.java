package com.processpuzzle.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContentPartsFlattenerTest {

    @Test
    void textPartsAreJoinedAndReasoningPartsDropped() {
        String line = "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":[{\"type\":\"thinking\",\"thinking\":[]},"
            + "{\"type\":\"text\",\"text\":\"Hi\"},{\"type\":\"text\",\"text\":\" you\"}]}}]}";

        assertThat(ContentPartsFlattener.flatten(line)).startsWith("data: ").contains("\"content\":\"Hi you\"")
            .doesNotContain("thinking");
    }

    @Test
    void aWholeMessageIsFlattenedToo() {
        String line = "data: {\"choices\":[{\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"Done\"}]}}]}";

        assertThat(ContentPartsFlattener.flatten(line)).contains("\"content\":\"Done\"");
    }

    @Test
    void aReasoningOnlyChunkBecomesEmptyText() {
        String line = "data: {\"choices\":[{\"delta\":{\"content\":[{\"type\":\"thinking\",\"thinking\":[]}]}}]}";

        assertThat(ContentPartsFlattener.flatten(line)).contains("\"content\":\"\"");
    }

    @Test
    void everythingElseIsLeftAsItIs() {
        for (String line : new String[] {"data: [DONE]", "", ": keep-alive", "event: ping",
            "data: {\"choices\":[{\"delta\":{\"content\":\"plain\"}}]}", "data: {not json \"content\":[",
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[]}}]}"}) {
            assertThat(ContentPartsFlattener.flatten(line)).isEqualTo(line);
        }
    }
}
