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
        DocumentContent content = DocumentContent.of(List.of(text, chart));

        assertThat(checker.check(DocumentPorts.empty(), content)).isEmpty();
    }

    @Test
    void danglingWidgetEmbedIsAnError() {
        DocumentBlock text = textBlockEmbedding("intro", "does-not-exist");
        DocumentContent content = DocumentContent.of(List.of(text));

        List<DocumentValidationProblem> problems = checker.check(DocumentPorts.empty(), content);
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
        DocumentContent content = DocumentContent.of(List.of(text, chart));

        assertThat(checker.check(DocumentPorts.empty(), content))
                .extracting(DocumentValidationProblem::errorId)
                .containsExactly("document.validation.dangling-widget-embed");
    }

    @Test
    void orphanedReferencedWidgetIsOnlyAWarning() {
        DocumentBlock chart = widget("chart-1", WidgetPlacement.REFERENCED, Map.of());
        DocumentContent content = DocumentContent.of(List.of(chart));

        List<DocumentValidationProblem> problems = checker.check(DocumentPorts.empty(), content);
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
        DocumentContent content = DocumentContent.of(List.of(tabGroup, tab1));

        assertThat(checker.check(DocumentPorts.empty(), content))
                .extracting(DocumentValidationProblem::errorId)
                .containsExactly("document.validation.dangling-child-id");
    }

    @Test
    void unknownPortInBindingsIsAnError() {
        DocumentBlock chart = new DocumentBlock("chart-1", BlockKind.WIDGET, null, null,
                WidgetPlacement.STANDALONE, "entity-grid", Map.of(),
                Map.of("rsqlFilter", "not-a-declared-port"), Map.of());
        DocumentContent content = DocumentContent.of(List.of(chart));

        assertThat(checker.check(DocumentPorts.empty(), content))
                .extracting(DocumentValidationProblem::errorId)
                .containsExactly("document.validation.unknown-port");
    }

    @Test
    void duplicateBlockIdIsAnError() {
        DocumentBlock a = widget("dup", WidgetPlacement.STANDALONE, Map.of());
        DocumentBlock b = widget("dup", WidgetPlacement.STANDALONE, Map.of());
        DocumentContent content = DocumentContent.of(List.of(a, b));

        assertThat(checker.check(DocumentPorts.empty(), content))
                .extracting(DocumentValidationProblem::errorId)
                .contains("document.validation.duplicate-block-id");
    }

    @Test
    void anUnknownOutputPortIsNamedAsAnOutputRatherThanAnInput() {
        DocumentBlock chart = new DocumentBlock("chart-1", BlockKind.WIDGET, null, null,
                WidgetPlacement.STANDALONE, "entity-grid", Map.of(), Map.of(),
                Map.of("selected", "not-a-declared-port"));

        assertThat(checker.check(DocumentPorts.empty(), DocumentContent.of(List.of(chart))))
                .singleElement().satisfies(problem -> {
                    assertThat(problem.errorId()).isEqualTo("document.validation.unknown-port");
                    assertThat(problem.errorText()).contains("is not a declared output port");
                    assertThat(problem.path()).isEqualTo("/blocks/0/outputBindings/selected");
                });
    }

    @Test
    void aChildIdPointingAtAStandaloneWidgetIsAsDanglingAsOneThatPointsNowhere() {
        // Only REFERENCED widgets are legal targets — the same rule widgetEmbed obeys.
        DocumentBlock tabGroup = widget("tabs", WidgetPlacement.STANDALONE, Map.of("childIds", List.of("tab-1")));
        DocumentBlock tab1 = widget("tab-1", WidgetPlacement.STANDALONE, Map.of());

        assertThat(checker.check(DocumentPorts.empty(), DocumentContent.of(List.of(tabGroup, tab1))))
                .extracting(DocumentValidationProblem::errorId)
                .containsExactly("document.validation.dangling-child-id");
    }

    @Test
    void childIdEntriesThatAreNotStringsAreIgnoredRatherThanCrashingTheCheck() {
        // props are a widget's own business and arrive from JSON, so the list can hold anything.
        DocumentBlock tabGroup = widget("tabs", WidgetPlacement.STANDALONE,
                Map.of("childIds", List.of(42, "tab-1")));
        DocumentBlock tab1 = widget("tab-1", WidgetPlacement.REFERENCED, Map.of());

        assertThat(checker.check(DocumentPorts.empty(), DocumentContent.of(List.of(tabGroup, tab1)))).isEmpty();
    }

    @Test
    void aChildIdsPropThatIsNotAListIsIgnoredToo() {
        DocumentBlock tabGroup = widget("tabs", WidgetPlacement.STANDALONE, Map.of("childIds", "tab-1"));

        assertThat(checker.check(DocumentPorts.empty(), DocumentContent.of(List.of(tabGroup)))).isEmpty();
    }

    @Test
    void aWidgetEmbedWithoutABlockIdIsMalformedRatherThanDangling() {
        JsonNode noAttrs = docContaining(json.createObjectNode().put("type", "widgetEmbed"));
        JsonNode nullBlockId = docContaining(json.createObjectNode().put("type", "widgetEmbed")
                .set("attrs", json.createObjectNode().putNull("blockId")));

        assertThat(checker.check(DocumentPorts.empty(), DocumentContent.of(List.of(text("a", noAttrs)))))
                .extracting(DocumentValidationProblem::errorId)
                .containsExactly("document.validation.malformed-widget-embed");
        assertThat(checker.check(DocumentPorts.empty(), DocumentContent.of(List.of(text("b", nullBlockId)))))
                .extracting(DocumentValidationProblem::errorId)
                .containsExactly("document.validation.malformed-widget-embed");
    }

    @Test
    void aBlockWhoseContentNodeIsMissingIsSimplyNotScanned() {
        DocumentBlock block = text("intro", com.fasterxml.jackson.databind.node.MissingNode.getInstance());

        assertThat(checker.check(DocumentPorts.empty(), DocumentContent.of(List.of(block)))).isEmpty();
    }

    @Test
    void referencesToFindsBothEmbedAndChildIdPointers() {
        DocumentBlock target = widget("chart-1", WidgetPlacement.REFERENCED, Map.of());
        DocumentBlock tabGroup = widget("tabs", WidgetPlacement.STANDALONE, Map.of("childIds", List.of("chart-1")));
        DocumentBlock text = textBlockEmbedding("intro", "chart-1");
        DocumentContent content = DocumentContent.of(List.of(target, tabGroup, text));

        assertThat(checker.referencesTo(content, "chart-1")).containsExactlyInAnyOrder("tabs", "intro");
    }

    @Test
    void referencesToFindsNothingWhenNoBlockPointsAtIt() {
        // Walks the same shapes as the matching case — prose, an unrelated embed, a widget with no
        // content at all — and has to come back empty for the delete to be allowed.
        DocumentBlock target = widget("chart-1", WidgetPlacement.REFERENCED, Map.of());
        DocumentBlock unrelatedWidget = widget("tabs", WidgetPlacement.STANDALONE, Map.of());
        DocumentBlock elsewhere = textBlockEmbedding("intro", "some-other-block");
        DocumentBlock attrsWithoutBlockId = text("odd",
                docContaining(json.createObjectNode().put("type", "widgetEmbed")));

        assertThat(checker.referencesTo(
                DocumentContent.of(List.of(target, unrelatedWidget, elsewhere, attrsWithoutBlockId)), "chart-1"))
                .isEmpty();
    }

    @Test
    void widgetCoverageReportsOnlyTheWidgetsATranslationIsMissing() {
        DocumentContent source = DocumentContent.of(List.of(
                widget("chart-1", WidgetPlacement.STANDALONE, Map.of()),
                widget("grid-1", WidgetPlacement.STANDALONE, Map.of())));
        DocumentContent translation = DocumentContent.of(List.of(
                widget("chart-1", WidgetPlacement.STANDALONE, Map.of())));

        assertThat(checker.checkWidgetCoverage(source, "en", translation)).singleElement()
                .satisfies(problem -> {
                    assertThat(problem.errorId()).isEqualTo("document.validation.widget-missing-from-translation");
                    assertThat(problem.errorText()).contains("grid-1").contains("'en'");
                    assertThat(problem.severity()).isEqualTo(Severity.WARNING);
                });
    }

    private JsonNode docContaining(JsonNode node) {
        return json.createObjectNode().put("type", "doc").set("content", json.createArrayNode().add(node));
    }

    private DocumentBlock text(String id, JsonNode content) {
        return new DocumentBlock(id, BlockKind.TEXT, true, content, null, null, Map.of(), Map.of(), Map.of());
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
