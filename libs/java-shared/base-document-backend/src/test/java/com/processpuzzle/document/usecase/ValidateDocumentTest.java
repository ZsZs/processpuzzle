package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.BlockKind;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentInputPort;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.PortType;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import com.processpuzzle.document.usecase.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValidateDocumentTest {

    private final ValidateDocument validateDocument =
            new ValidateDocument(new DocumentReferentialIntegrityChecker());

    @Test
    void aDocumentWithNoTranslationsHasNothingToBeWrongWithIt() {
        ValidateDocument.ValidationOutcome outcome = validateDocument.execute(input());

        assertThat(outcome.valid()).isTrue();
        assertThat(outcome.problems()).isEmpty();
    }

    @Test
    void anOmittedTranslationsArrayIsTheSameAsAnEmptyOne() {
        ValidateDocument.ValidationOutcome outcome = validateDocument.execute(input(null));

        assertThat(outcome.valid()).isTrue();
        assertThat(outcome.problems()).isEmpty();
    }

    @Test
    void eachProblemIsAttributedToTheTranslationItWasFoundIn() {
        // The checker is locale-agnostic by design, so attributing the finding is this use case's job.
        ValidateDocument.ValidationOutcome outcome = validateDocument.execute(input(List.of(
                translation("en", widgetBoundTo("grid-1", "customer")),
                translation("de", widgetBoundTo("grid-1", "gone")))));

        assertThat(outcome.valid()).isFalse();
        assertThat(outcome.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.locale()).isEqualTo("de");
            assertThat(problem.errorId()).isEqualTo("document.validation.unknown-port");
            assertThat(problem.severity()).isEqualTo(Severity.ERROR);
        });
    }

    @Test
    void aWidgetMissingFromATranslationIsAWarningReportedAgainstThatTranslation() {
        // Translators legitimately split and merge prose, but a widget is functionality — one
        // silently absent from a language is worth saying, without blocking the save.
        ValidateDocument.ValidationOutcome outcome = validateDocument.execute(input(List.of(
                translation("en", widgetBoundTo("grid-1", "customer")),
                translation("de", textBlock("einleitung")))));

        assertThat(outcome.valid()).isTrue();
        assertThat(outcome.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.locale()).isEqualTo("de");
            assertThat(problem.errorId()).isEqualTo("document.validation.widget-missing-from-translation");
            assertThat(problem.severity()).isEqualTo(Severity.WARNING);
        });
    }

    @Test
    void theSourceLocaleIsNotComparedAgainstItself() {
        ValidateDocument.ValidationOutcome outcome = validateDocument.execute(input(List.of(
                translation("en", widgetBoundTo("grid-1", "customer")))));

        assertThat(outcome.problems()).isEmpty();
    }

    @Test
    void withoutTheSourceLocaleInThePayloadThereIsNothingToCompareCoverageAgainst() {
        ValidateDocument.ValidationOutcome outcome = validateDocument.execute(input(List.of(
                translation("de", textBlock("einleitung")))));

        assertThat(outcome.valid()).isTrue();
        assertThat(outcome.problems()).isEmpty();
    }

    @Test
    void aTranslationThatCarriesNoBlocksAtAllIsValidatedAsAnEmptyOne() {
        ValidateDocument.ValidationOutcome outcome = validateDocument.execute(input(List.of(
                translation("en", widgetBoundTo("grid-1", "customer")),
                new DocumentTranslationCommand("de", null))));

        assertThat(outcome.problems())
                .extracting(DocumentValidationProblem::errorId)
                .containsExactly("document.validation.widget-missing-from-translation");
    }

    @Test
    void duplicateBlockIdsWithinOneTranslationBlockTheDocument() {
        ValidateDocument.ValidationOutcome outcome = validateDocument.execute(input(List.of(
                translation("en", textBlock("intro"), textBlock("intro")))));

        assertThat(outcome.valid()).isFalse();
        assertThat(outcome.problems())
                .extracting(DocumentValidationProblem::errorId)
                .containsExactly("document.validation.duplicate-block-id");
    }

    // region fixtures
    private static DocumentCommand input() {
        return input(List.of());
    }

    private static DocumentCommand input(List<DocumentTranslationCommand> translations) {
        return new DocumentCommand(new DocumentProperties("getting-started", "Getting started", null, null, null,
                "en", false, null, new DocumentPorts(List.of(new DocumentInputPort("customer",
                PortType.ENTITY_REF, true, null, null, "Customer", null, null)), List.of())), translations);
    }

    private static DocumentTranslationCommand translation(String locale, DocumentBlock... blocks) {
        return new DocumentTranslationCommand(locale, DocumentContent.of(List.of(blocks)));
    }

    private static DocumentBlock textBlock(String id) {
        return new DocumentBlock(id, BlockKind.TEXT, true, null, null, null, null, null, null);
    }

    private static DocumentBlock widgetBoundTo(String id, String portName) {
        return new DocumentBlock(id, BlockKind.WIDGET, null, null, null, "entity-grid", null,
                Map.of("rows", portName), null);
    }
    // endregion
}
