package com.processpuzzle.document.usecase;

import com.processpuzzle.document.usecase.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentValidationProblemTest {

    @Test
    void aProblemIsAnErrorUnlessItSaysOtherwise() {
        DocumentValidationProblem problem = new DocumentValidationProblem("/blocks/0", "id", "text");

        assertThat(problem.severity()).isEqualTo(Severity.ERROR);
        assertThat(problem.locale()).isNull();
        assertThat(problem.blocksPersisting()).isTrue();
    }

    @Test
    void aWarningDoesNotBlockAWrite() {
        DocumentValidationProblem warning =
                new DocumentValidationProblem("/blocks/0", "id", "text", Severity.WARNING);

        assertThat(warning.blocksPersisting()).isFalse();
    }

    @Test
    void attributingTheLocaleLeavesEverythingElseAlone() {
        // The checker is deliberately locale-agnostic, so stamping the locale is the caller's job.
        DocumentValidationProblem stamped =
                new DocumentValidationProblem("/blocks/0", "id", "text", Severity.WARNING).withLocale("de");

        assertThat(stamped.locale()).isEqualTo("de");
        assertThat(stamped.path()).isEqualTo("/blocks/0");
        assertThat(stamped.errorId()).isEqualTo("id");
        assertThat(stamped.errorText()).isEqualTo("text");
        assertThat(stamped.severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void theListHelpersFilterAndStampInPlaceOfALoop() {
        List<DocumentValidationProblem> problems = List.of(
                new DocumentValidationProblem("/blocks/0", "error", "text"),
                new DocumentValidationProblem("/blocks/1", "warning", "text", Severity.WARNING));

        assertThat(DocumentValidationProblem.blocking(problems))
                .extracting(DocumentValidationProblem::errorId).containsExactly("error");
        assertThat(DocumentValidationProblem.withLocale(problems, "en"))
                .extracting(DocumentValidationProblem::locale).containsExactly("en", "en");
    }

    @Test
    void bothListHelpersTolerateNothingAtAll() {
        assertThat(DocumentValidationProblem.blocking(null)).isEmpty();
        assertThat(DocumentValidationProblem.withLocale(null, "en")).isEmpty();
    }
}
