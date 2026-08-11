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
 * What matters here is the status each exception maps to — that is the module's half of the HTTP
 * contract — plus the {@code error} body key, which every other handler in the codebase uses and
 * which the contract's own schema disagrees with (see the handler's own note).
 */
class DocumentApiExceptionHandlerTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private final DocumentApiExceptionHandler handler = new DocumentApiExceptionHandler();

    @Test
    void aMissingDocumentTranslationOrBlockIs404() {
        assertThat(handler.handleNotFound(new DocumentNotFoundException(ORG, ID)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleBlockNotFound(new DocumentBlockNotFoundException(ORG, ID, "intro")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleTranslationNotFound(new DocumentTranslationNotFoundException(ORG, ID, "de"))
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theBodyKeyIsErrorAndCarriesTheExceptionsOwnMessage() {
        var response = handler.handleNotFound(new DocumentNotFoundException(ORG, ID));

        assertThat(response.getBody()).containsEntry("error", "No document '" + ID + "' in organization '" + ORG + "'");
    }

    @Test
    void everyFlavourOfAlreadyExistsIs409() {
        assertThat(handler.handleConflict(new DocumentAlreadyExistsException(ORG, ID)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.handleSlugConflict(new DocumentSlugAlreadyExistsException(ORG, "taken")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.handleTranslationConflict(new DocumentTranslationAlreadyExistsException(ID, "de"))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.handlePublishingConflict(
                DocumentPublishingConflictException.nothingToRevertTo(ID, "en")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void aDeniedActionIs403RatherThan404() {
        // The 404-for-invisible-documents case never reaches this handler: the use case throws
        // DocumentNotFoundException instead. See DocumentAccessDeniedException for why.
        var response = handler.handleAccessDenied(DocumentAccessDeniedException.lacksRole("edit", ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("error", "The current principal may not edit document '" + ID + "'");
    }

    @Test
    void aStillReferencedBlockAnswers409WithTheReferrersSoTheUiCanPointAtThem() {
        var response = handler.handleBlockReferenced(
                new DocumentBlockReferencedException("grid-1", List.of("intro", "outro")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("referencingBlockIds", List.of("intro", "outro"));
        assertThat(response.getBody().get("error").toString()).contains("grid-1");
    }

    @Test
    void aStaleWriteAnswers409WithAdviceRatherThanHibernatesMessage() {
        var response = handler.handleStaleWrite(new OptimisticLockingFailureException("Row was updated or deleted"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry(
                "error", "This document was modified by someone else — reload and retry.");
    }
}
