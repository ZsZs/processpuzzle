package com.processpuzzle.artifact.usecase.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processpuzzle.artifact.domain.*;
import com.processpuzzle.artifact.usecase.ArtifactValidationProblem;
import com.processpuzzle.rule.domain.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactReferentialIntegrityCheckerTest {

    private final ArtifactReferentialIntegrityChecker checker = new ArtifactReferentialIntegrityChecker();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void cleanGraphHasNoProblems() {
        ArtifactBlock chart = widget("chart-1", WidgetPlacement.REFERENCED, Map.of());
        ArtifactBlock text = textBlockEmbedding("intro", "chart-1");
        ArtifactGraph graph = new ArtifactGraph(List.of(), List.of(), List.of(text, chart));

        assertThat(checker.check(graph)).isEmpty();
    }

    @Test
    void danglingWidgetEmbedIsAnError() {
        ArtifactBlock text = textBlockEmbedding("intro", "does-not-exist");
        ArtifactGraph graph = new ArtifactGraph(List.of(), List.of(), List.of(text));

        List<ArtifactValidationProblem> problems = checker.check(graph);
        assertThat(problems).hasSize(1);
        assertThat(problems.get(0).errorId()).isEqualTo("artifact.validation.dangling-widget-embed");
        assertThat(problems.get(0).severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void standaloneWidgetReferencedByEmbedIsAlsoAnError() {
        // placement STANDALONE, but something points at it anyway — still dangling by our
        // definition, since only REFERENCED widgets are legal targets.
        ArtifactBlock chart = widget("chart-1", WidgetPlacement.STANDALONE, Map.of());
        ArtifactBlock text = textBlockEmbedding("intro", "chart-1");
        ArtifactGraph graph = new ArtifactGraph(List.of(), List.of(), List.of(text, chart));

        assertThat(checker.check(graph))
                .extracting(ArtifactValidationProblem::errorId)
                .containsExactly("artifact.validation.dangling-widget-embed");
    }

    @Test
    void orphanedReferencedWidgetIsOnlyAWarning() {
        ArtifactBlock chart = widget("chart-1", WidgetPlacement.REFERENCED, Map.of());
        ArtifactGraph graph = new ArtifactGraph(List.of(), List.of(), List.of(chart));

        List<ArtifactValidationProblem> problems = checker.check(graph);
        assertThat(problems).hasSize(1);
        assertThat(problems.get(0).severity()).isEqualTo(Severity.WARNING);
        assertThat(ArtifactValidationProblem.blocking(problems)).isEmpty();
    }

    @Test
    void danglingChildIdIsAnError() {
        ArtifactBlock tabGroup = widget("tabs", WidgetPlacement.STANDALONE,
                Map.of("childIds", List.of("tab-1", "tab-2")));
        ArtifactBlock tab1 = widget("tab-1", WidgetPlacement.REFERENCED, Map.of());
        // tab-2 is missing entirely.
        ArtifactGraph graph = new ArtifactGraph(List.of(), List.of(), List.of(tabGroup, tab1));

        assertThat(checker.check(graph))
                .extracting(ArtifactValidationProblem::errorId)
                .containsExactly("artifact.validation.dangling-child-id");
    }

    @Test
    void unknownPortInBindingsIsAnError() {
        ArtifactBlock chart = new ArtifactBlock("chart-1", BlockKind.WIDGET, null, null,
                WidgetPlacement.STANDALONE, "entity-grid", Map.of(),
                Map.of("rsqlFilter", "not-a-declared-port"), Map.of());
        ArtifactGraph graph = new ArtifactGraph(List.of(), List.of(), List.of(chart));

        assertThat(checker.check(graph))
                .extracting(ArtifactValidationProblem::errorId)
                .containsExactly("artifact.validation.unknown-port");
    }

    @Test
    void duplicateBlockIdIsAnError() {
        ArtifactBlock a = widget("dup", WidgetPlacement.STANDALONE, Map.of());
        ArtifactBlock b = widget("dup", WidgetPlacement.STANDALONE, Map.of());
        ArtifactGraph graph = new ArtifactGraph(List.of(), List.of(), List.of(a, b));

        assertThat(checker.check(graph))
                .extracting(ArtifactValidationProblem::errorId)
                .contains("artifact.validation.duplicate-block-id");
    }

    @Test
    void referencesToFindsBothEmbedAndChildIdPointers() {
        ArtifactBlock target = widget("chart-1", WidgetPlacement.REFERENCED, Map.of());
        ArtifactBlock tabGroup = widget("tabs", WidgetPlacement.STANDALONE, Map.of("childIds", List.of("chart-1")));
        ArtifactBlock text = textBlockEmbedding("intro", "chart-1");
        ArtifactGraph graph = new ArtifactGraph(List.of(), List.of(), List.of(target, tabGroup, text));

        assertThat(checker.referencesTo(graph, "chart-1")).containsExactlyInAnyOrder("tabs", "intro");
    }

    private ArtifactBlock widget(String id, WidgetPlacement placement, Map<String, Object> props) {
        return new ArtifactBlock(id, BlockKind.WIDGET, null, null, placement, "some-widget", props, Map.of(), Map.of());
    }

    private ArtifactBlock textBlockEmbedding(String id, String embeddedBlockId) {
        JsonNode content = json.createObjectNode()
                .put("type", "doc")
                .set("content", json.createArrayNode().add(
                        json.createObjectNode().put("type", "widgetEmbed")
                                .set("attrs", json.createObjectNode().put("blockId", embeddedBlockId))));
        return new ArtifactBlock(id, BlockKind.TEXT, true, content, null, null, Map.of(), Map.of(), Map.of());
    }
}
