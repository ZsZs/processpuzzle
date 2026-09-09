package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The result records each normalise their one collection, so a caller never has to null-check a list
 * that came out of a use case.
 */
class UseCaseResultRecordsTest {

    @Test
    void documentDetailsHasNoTranslationStatesRatherThanNullOnes() {
        Document document = new Document("demo", "11111111-1111-1111-1111-111111111111",
                "getting-started", "Getting started", "en", "ada");

        DocumentDetails details = new DocumentDetails(document, null, null);

        assertThat(details.states()).isEmpty();
        assertThat(details.selected()).isNull();
        assertThat(details.document()).isSameAs(document);
    }

    @Test
    void anImportOutcomeHasNoErrorsRatherThanNullOnes() {
        ImportOutcome outcome = new ImportOutcome(2, 1, null);

        assertThat(outcome.errors()).isEmpty();
        assertThat(outcome.created()).isEqualTo(2);
        assertThat(outcome.updated()).isEqualTo(1);
    }

    @Test
    void thePublicViewHasNoAvailableLocalesRatherThanNullOnes() {
        FindPublishedContent.PublishedContentView view =
                new FindPublishedContent.PublishedContentView(null, null, false, null);

        assertThat(view.availableLocales()).isEmpty();
    }
}
