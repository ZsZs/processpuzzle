package com.processpuzzle.document.usecase;

import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.model.DocumentBlockInput;
import com.processpuzzle.document.model.DocumentInput;
import com.processpuzzle.document.model.DocumentTranslationInput;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import com.processpuzzle.document.usecase.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValidateDocumentTest {

    private final ValidateDocument validateDocument =
            new ValidateDocument(new DocumentReferentialIntegrityChecker(), new DocumentMapper());

    @Test
    void aDocumentWithNoTranslationsHasNothingToBeWrongWithIt() {
        ValidateDocument.ValidationOutcome outcome = validateDocument.execute(input());

        assertThat(outcome.valid()).isTrue();
        assertThat(outcome.problems()).isEmpty();
    }

    @Test
    void anOmittedTranslationsArrayIsTheSameAsAnEmptyOne() {
        ValidateDocument.ValidationOutcome outcome = validateDocument.execute(input().translations(null));

        assertThat(outcome.valid()).isTrue();
        assertThat(outcome.problems()).isEmpty();
    }

    @Test
    void eachProblemIsAttributedToTheTranslationItWasFoundIn() {
        // The checker is locale-agnostic by design, so attributing the finding is this use case's job.
        ValidateDocument.ValidationOutcome outcome = validateDocument.execute(input().translations(List.of(
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
        ValidateDocument.ValidationOutcome outcome = validateDocument.execute(input().translations(List.of(
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
        ValidateDocument.ValidationOutcome outcome = validateDocument.execute(input().translations(List.of(
                translation("en", widgetBoundTo("grid-1", "customer")))));

        assertThat(outcome.problems()).isEmpty();
    }

    @Test
    void withoutTheSourceLocaleInThePayloadThereIsNothingToCompareCoverageAgainst() {
        ValidateDocument.ValidationOutcome outcome = validateDocument.execute(input().translations(List.of(
                translation("de", textBlock("einleitung")))));

        assertThat(outcome.valid()).isTrue();
        assertThat(outcome.problems()).isEmpty();
    }

    @Test
    void aTranslationThatCarriesNoBlocksAtAllIsValidatedAsAnEmptyOne() {
        ValidateDocument.ValidationOutcome outcome = validateDocument.execute(input().translations(List.of(
                translation("en", widgetBoundTo("grid-1", "customer")),
                new DocumentTranslationInput().locale("de"))));

        assertThat(outcome.problems())
                .extracting(DocumentValidationProblem::errorId)
                .containsExactly("document.validation.widget-missing-from-translation");
    }

    @Test
    void duplicateBlockIdsWithinOneTranslationBlockTheDocument() {
        ValidateDocument.ValidationOutcome outcome = validateDocument.execute(input().translations(List.of(
                translation("en", textBlock("intro"), textBlock("intro")))));

        assertThat(outcome.valid()).isFalse();
        assertThat(outcome.problems())
                .extracting(DocumentValidationProblem::errorId)
                .containsExactly("document.validation.duplicate-block-id");
    }

    // region fixtures
    private static DocumentInput input() {
        return new DocumentInput()
                .slug("getting-started")
                .title("Getting started")
                .sourceLocale("en")
                .inputPorts(List.of(new com.processpuzzle.document.model.DocumentInputPort()
                        .name("customer")
                        .type(com.processpuzzle.document.model.PortType.ENTITY_REF)
                        .required(true)
                        .entityType("Customer")));
    }

    private static DocumentTranslationInput translation(String locale, DocumentBlockInput... blocks) {
        return new DocumentTranslationInput().locale(locale).blocks(List.of(blocks));
    }

    private static DocumentBlockInput textBlock(String id) {
        return new DocumentBlockInput().id(id).kind(com.processpuzzle.document.model.BlockKind.TEXT);
    }

    private static DocumentBlockInput widgetBoundTo(String id, String portName) {
        return new DocumentBlockInput().id(id)
                .kind(com.processpuzzle.document.model.BlockKind.WIDGET)
                .type("entity-grid")
                .inputBindings(Map.of("rows", portName));
    }
    // endregion
}
