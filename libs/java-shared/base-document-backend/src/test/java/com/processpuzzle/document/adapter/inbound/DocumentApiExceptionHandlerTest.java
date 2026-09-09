package com.processpuzzle.document.adapter.inbound;

import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import com.processpuzzle.document.usecase.exception.DocumentAlreadyExistsException;
import com.processpuzzle.document.usecase.exception.DocumentBlockNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentBlockReferencedException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentPublishingConflictException;
import com.processpuzzle.document.usecase.exception.DocumentSlugAlreadyExistsException;
import com.processpuzzle.document.usecase.exception.DocumentTranslationAlreadyExistsException;
import com.processpuzzle.document.usecase.exception.DocumentTranslationNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two things matter here: the status each exception maps to, and the {@code errorId}. Clients branch
 * on the id, and any other implementation of the same yaml has to answer the same refusals with the
 * same ids, so the ids asserted below are a contract rather than an internal detail.
 */
class DocumentApiExceptionHandlerTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private final DocumentApiExceptionHandler handler = new DocumentApiExceptionHandler();

    @Test
    void aMissingDocumentTranslationOrBlockIs404WithItsOwnId() {
        var document = handler.handleNotFound(new DocumentNotFoundException(ORG, ID));
        var block = handler.handleBlockNotFound(new DocumentBlockNotFoundException(ORG, ID, "intro"));
        var translation = handler.handleTranslationNotFound(new DocumentTranslationNotFoundException(ORG, ID, "de"));

        assertThat(document.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(block.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(translation.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(document.getBody().getErrorId()).isEqualTo("document.not-found");
        assertThat(block.getBody().getErrorId()).isEqualTo("document.block.not-found");
        assertThat(translation.getBody().getErrorId()).isEqualTo("document.translation.not-found");
    }

    @Test
    void errorTextCarriesTheExceptionsOwnMessage() {
        var response = handler.handleNotFound(new DocumentNotFoundException(ORG, ID));

        assertThat(response.getBody().getErrorText()).isEqualTo("No document '" + ID + "' in organization '" + ORG + "'");
    }

    @Test
    void everyFlavourOfAlreadyExistsIs409WithItsOwnId() {
        var document = handler.handleConflict(new DocumentAlreadyExistsException(ORG, ID));
        var slug = handler.handleSlugConflict(new DocumentSlugAlreadyExistsException(ORG, "taken"));
        var translation = handler.handleTranslationConflict(new DocumentTranslationAlreadyExistsException(ID, "de"));

        assertThat(document.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(slug.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(translation.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(document.getBody().getErrorId()).isEqualTo("document.already-exists");
        assertThat(slug.getBody().getErrorId()).isEqualTo("document.slug.already-exists");
        assertThat(translation.getBody().getErrorId()).isEqualTo("document.translation.already-exists");
    }

    /**
     * The three publishing refusals share a status, so the id is the only thing that tells them apart —
     * "fix the content first" is a different instruction to the user than "there is nothing to revert to".
     */
    @Test
    void eachPublishingConflictKeepsItsOwnIdDespiteSharingTheStatus() {
        var nothingToRevert = handler.handlePublishingConflict(DocumentPublishingConflictException.nothingToRevertTo(ID, "en"));
        var notPublishable = handler.handlePublishingConflict(DocumentPublishingConflictException.notPublishable(ID, "en", List.of("problem")));
        var sourceLocale = handler.handlePublishingConflict(DocumentPublishingConflictException.sourceLocaleNotRemovable(ID, "en"));

        assertThat(nothingToRevert.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(nothingToRevert.getBody().getErrorId()).isEqualTo("document.draft.nothing-to-revert-to");
        assertThat(notPublishable.getBody().getErrorId()).isEqualTo("document.publish.not-publishable");
        // Matches the Cloud Function's id for the same refusal.
        assertThat(sourceLocale.getBody().getErrorId()).isEqualTo("document.translation.source-locale-not-removable");
    }

    @Test
    void aDeniedActionIs403RatherThan404() {
        // The 404-for-invisible-documents case never reaches this handler: the use case throws
        // DocumentNotFoundException instead. See DocumentAccessDeniedException for why.
        var response = handler.handleAccessDenied(DocumentAccessDeniedException.lacksRole("edit", ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getErrorId()).isEqualTo("document.access-denied");
        assertThat(response.getBody().getErrorText()).isEqualTo("The current principal may not edit document '" + ID + "'");
    }

    /**
     * The referrers stay in {@code errorText} rather than a second key: an undeclared
     * {@code referencingBlockIds} was a shape only this implementation knew about, and no client read it.
     */
    @Test
    void aStillReferencedBlockAnswers409NamingTheReferrersInErrorText() {
        var response = handler.handleBlockReferenced(new DocumentBlockReferencedException("grid-1", List.of("intro", "outro")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getErrorId()).isEqualTo("document.block.referenced");
        assertThat(response.getBody().getErrorText()).contains("grid-1").contains("intro").contains("outro");
    }

    @Test
    void aStaleWriteAnswers409WithAdviceRatherThanHibernatesMessage() {
        var response = handler.handleStaleWrite(new OptimisticLockingFailureException("Row was updated or deleted"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getErrorId()).isEqualTo("document.stale-write");
        assertThat(response.getBody().getErrorText()).isEqualTo("This document was modified by someone else — reload and retry.");
    }
}
