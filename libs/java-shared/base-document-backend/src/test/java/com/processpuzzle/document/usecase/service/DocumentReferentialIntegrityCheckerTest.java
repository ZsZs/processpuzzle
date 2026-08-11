package com.processpuzzle.document.usecase.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processpuzzle.document.domain.*;
import com.processpuzzle.document.usecase.DocumentValidationProblem;
import com.processpuzzle.rule.domain.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentReferentialIntegrityCheckerTest {

    private final DocumentReferentialIntegrityChecker checker = new DocumentReferentialIntegrityChecker();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void cleanGraphHasNoProblems() {
        DocumentBlock chart = widget("chart-1", WidgetPlacement.REFERENCED, Map.of());
        DocumentBlock text = textBlockEmbedding("intro", "chart-1");
        DocumentGraph graph = new DocumentGraph(List.of(), List.of(), List.of(text, chart));

        assertThat(checker.check(graph)).isEmpty();
    }

    @Test
    void danglingWidgetEmbedIsAnError() {
        DocumentBlock text = textBlockEmbedding("intro", "does-not-exist");
        DocumentGraph graph = new DocumentGraph(List.of(), List.of(), List.of(text));

        List<DocumentValidationProblem> problems = checker.check(graph);
        assertThat(problems).hasSize(1);
        assertThat(problems.get(0).errorId()).isEqualTo("document.validation.dangling-widget-embed");
        assertThat(problems.get(0).severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void standaloneWidgetReferencedByEmbedIsAlsoAnError() {
        // placement STANDALONE, but something points at it anyway — still dangling by our
        // definition, since only REFERENCED widgets are legal targets.
        DocumentBlock chart = widget("chart-1", WidgetPlacement.STANDALONE, Map.of());
        DocumentBlock text = textBlockEmbedding("intro", "chart-1");
        DocumentGraph graph = new DocumentGraph(List.of(), List.of(), List.of(text, chart));

        assertThat(checker.check(graph))
                .extracting(DocumentValidationProblem::errorId)
                .containsExactly("document.validation.dangling-widget-embed");
    }

    @Test
    void orphanedReferencedWidgetIsOnlyAWarning() {
        DocumentBlock chart = widget("chart-1", WidgetPlacement.REFERENCED, Map.of());
        DocumentGraph graph = new DocumentGraph(List.of(), List.of(), List.of(chart));

        List<DocumentValidationProblem> problems = checker.check(graph);
        assertThat(problems).hasSize(1);
        assertThat(problems.get(0).severity()).isEqualTo(Severity.WARNING);
        assertThat(DocumentValidationProblem.blocking(problems)).isEmpty();
    }

    @Test
    void danglingChildIdIsAnError() {
        DocumentBlock tabGroup = widget("tabs", WidgetPlacement.STANDALONE,
                Map.of("childIds", List.of("tab-1", "tab-2")));
        DocumentBlock tab1 = widget("tab-1", WidgetPlacement.REFERENCED, Map.of());
        // tab-2 is missing entirely.
        DocumentGraph graph = new DocumentGraph(List.of(), List.of(), List.of(tabGroup, tab1));

        assertThat(checker.check(graph))
                .extracting(DocumentValidationProblem::errorId)
                .containsExactly("document.validation.dangling-child-id");
    }

    @Test
    void unknownPortInBindingsIsAnError() {
        DocumentBlock chart = new DocumentBlock("chart-1", BlockKind.WIDGET, null, null,
                WidgetPlacement.STANDALONE, "entity-grid", Map.of(),
                Map.of("rsqlFilter", "not-a-declared-port"), Map.of());
        DocumentGraph graph = new DocumentGraph(List.of(), List.of(), List.of(chart));

        assertThat(checker.check(graph))
                .extracting(DocumentValidationProblem::errorId)
                .containsExactly("document.validation.unknown-port");
    }

    @Test
    void duplicateBlockIdIsAnError() {
        DocumentBlock a = widget("dup", WidgetPlacement.STANDALONE, Map.of());
        DocumentBlock b = widget("dup", WidgetPlacement.STANDALONE, Map.of());
        DocumentGraph graph = new DocumentGraph(List.of(), List.of(), List.of(a, b));

        assertThat(checker.check(graph))
                .extracting(DocumentValidationProblem::errorId)
                .contains("document.validation.duplicate-block-id");
    }

    @Test
    void referencesToFindsBothEmbedAndChildIdPointers() {
        DocumentBlock target = widget("chart-1", WidgetPlacement.REFERENCED, Map.of());
        DocumentBlock tabGroup = widget("tabs", WidgetPlacement.STANDALONE, Map.of("childIds", List.of("chart-1")));
        DocumentBlock text = textBlockEmbedding("intro", "chart-1");
        DocumentGraph graph = new DocumentGraph(List.of(), List.of(), List.of(target, tabGroup, text));

        assertThat(checker.referencesTo(graph, "chart-1")).containsExactlyInAnyOrder("tabs", "intro");
    }

    private DocumentBlock widget(String id, WidgetPlacement placement, Map<String, Object> props) {
        return new DocumentBlock(id, BlockKind.WIDGET, null, null, placement, "some-widget", props, Map.of(), Map.of());
    }

    private DocumentBlock textBlockEmbedding(String id, String embeddedBlockId) {
        JsonNode content = json.createObjectNode()
                .put("type", "doc")
                .set("content", json.createArrayNode().add(
                        json.createObjectNode().put("type", "widgetEmbed")
                                .set("attrs", json.createObjectNode().put("blockId", embeddedBlockId))));
        return new DocumentBlock(id, BlockKind.TEXT, true, content, null, null, Map.of(), Map.of(), Map.of());
    }
}
