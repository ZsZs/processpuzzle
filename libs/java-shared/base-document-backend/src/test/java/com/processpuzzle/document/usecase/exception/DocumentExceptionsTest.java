package com.processpuzzle.document.usecase.exception;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The messages are the API here: they are what {@code DocumentApiExceptionHandler} puts in the
 * {@code errorText} field of the response body, so they are read by people rather than by code. What
 * code branches on is the {@code errorId} beside them, asserted in
 * {@code DocumentApiExceptionHandlerTest}.
 */
class DocumentExceptionsTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    @Test
    void notFoundNamesTheDocumentAndTheOrganization() {
        assertThat(new DocumentNotFoundException(ORG, ID))
                .hasMessage("No document '" + ID + "' in organization '" + ORG + "'");
    }

    @Test
    void aMissingBlockNamesTheBlockAsWell() {
        assertThat(new DocumentBlockNotFoundException(ORG, ID, "intro"))
                .hasMessage("No block 'intro' in document '" + ID + "' (organization '" + ORG + "')");
    }

    @Test
    void aMissingTranslationNamesTheLocale() {
        assertThat(new DocumentTranslationNotFoundException(ORG, ID, "de"))
                .hasMessage("Document '" + ID + "' has no translation for locale 'de' in organization '" + ORG + "'");
    }

    @Test
    void theUnpublishedFlavourSaysNoPublishedContentRatherThanNoTranslation() {
        // A locale that exists but has never gone live is a different thing from one that is missing.
        assertThat(DocumentTranslationNotFoundException.unpublished(ID, "de"))
                .hasMessage("Document '" + ID + "' has no published content for locale 'de'");
    }

    @Test
    void theAlreadyExistsFlavoursNameWhatCollided() {
        assertThat(new DocumentAlreadyExistsException(ORG, ID))
                .hasMessage("Document '" + ID + "' already exists in organization '" + ORG + "'");
        assertThat(new DocumentSlugAlreadyExistsException(ORG, "taken"))
                .hasMessage("A document with slug 'taken' already exists in organization '" + ORG + "'");
        assertThat(new DocumentTranslationAlreadyExistsException(ID, "de"))
                .hasMessage("Document '" + ID + "' already has a translation for locale 'de'");
    }

    @Test
    void accessDeniedSaysWhichActionWasRefused() {
        assertThat(DocumentAccessDeniedException.lacksRole("publish", ID))
                .hasMessage("The current principal may not publish document '" + ID + "'");
        assertThat(new DocumentAccessDeniedException("custom")).hasMessage("custom");
    }

    @Test
    void eachPublishingConflictExplainsWhyItIsRefusedRatherThanJustThatItIs() {
        assertThat(DocumentPublishingConflictException.notPublishable(ID, "en", List.of("a problem")))
                .hasMessageContaining("has validation errors and cannot be published")
                .hasMessageContaining("a problem");
        assertThat(DocumentPublishingConflictException.nothingToRevertTo(ID, "en"))
                .hasMessageContaining("has never been published");
        assertThat(DocumentPublishingConflictException.sourceLocaleNotRemovable(ID, "en"))
                .hasMessageContaining("is the source locale")
                .hasMessageContaining("change sourceLocale first");
        assertThat(new DocumentPublishingConflictException("custom.id", "custom")).hasMessage("custom");
    }

    @Test
    void eachPublishingConflictCarriesItsOwnErrorIdBecauseTheyShareAStatus() {
        assertThat(DocumentPublishingConflictException.notPublishable(ID, "en", List.of("a problem")).getErrorId())
                .isEqualTo("document.publish.not-publishable");
        assertThat(DocumentPublishingConflictException.nothingToRevertTo(ID, "en").getErrorId())
                .isEqualTo("document.draft.nothing-to-revert-to");
        assertThat(DocumentPublishingConflictException.sourceLocaleNotRemovable(ID, "en").getErrorId())
                .isEqualTo("document.translation.source-locale-not-removable");
    }

    @Test
    void aReferencedBlockCarriesItsReferrersSoTheUiCanPointAtThem() {
        List<String> referrers = new ArrayList<>(List.of("intro", "outro"));
        DocumentBlockReferencedException thrown = new DocumentBlockReferencedException("grid-1", referrers);
        referrers.clear();

        assertThat(thrown).hasMessage("Block 'grid-1' is still referenced by: [intro, outro]");
        assertThat(thrown.getReferencingBlockIds()).containsExactly("intro", "outro");
    }
}
