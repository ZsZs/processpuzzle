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
import com.processpuzzle.shared.model.ErrorResponse;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Same shape as {@code RuleApiExceptionHandler} — module-specific exceptions only.
 * {@code IllegalArgumentException} (referential-integrity failures raised as 400) and
 * {@code IllegalStateException} are already handled generically by core's
 * {@code ApiExceptionHandler}, so nothing here duplicates those.
 *
 * <p>Bodies are the {@code ErrorResponse} of base-document-api.yaml: {@code errorId} plus
 * {@code errorText}. The ids are the same strings the Cloud Function emits for the same refusals
 * (see {@code tools/firebase/functions/src/base-document/base-document.handlers.ts}) — that is the
 * point of them. A client is served by whichever backend the deployment binds, and an id that differed
 * between the two would make the platform visible in exactly the place a client branches on it.
 */
@RestControllerAdvice
public class DocumentApiExceptionHandler {

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(DocumentNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "document.not-found", ex.getMessage());
    }

    @ExceptionHandler(DocumentBlockNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBlockNotFound(DocumentBlockNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "document.block.not-found", ex.getMessage());
    }

    @ExceptionHandler(DocumentTranslationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTranslationNotFound(DocumentTranslationNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "document.translation.not-found", ex.getMessage());
    }

    @ExceptionHandler(DocumentAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleConflict(DocumentAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, "document.already-exists", ex.getMessage());
    }

    @ExceptionHandler(DocumentSlugAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleSlugConflict(DocumentSlugAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, "document.slug.already-exists", ex.getMessage());
    }

    @ExceptionHandler(DocumentTranslationAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleTranslationConflict(DocumentTranslationAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, "document.translation.already-exists", ex.getMessage());
    }

    /** The id comes from the exception: three distinct refusals share this status. */
    @ExceptionHandler(DocumentPublishingConflictException.class)
    public ResponseEntity<ErrorResponse> handlePublishingConflict(DocumentPublishingConflictException ex) {
        return error(HttpStatus.CONFLICT, ex.getErrorId(), ex.getMessage());
    }

    /**
     * 403 — and note what does <em>not</em> arrive here: a reader who may not see a document at all
     * gets {@link DocumentNotFoundException} instead, so 404 above. See
     * {@link DocumentAccessDeniedException} for why the two cases answer differently.
     */
    @ExceptionHandler(DocumentAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(DocumentAccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "document.access-denied", ex.getMessage());
    }

    /**
     * The referring block ids stay inside {@code errorText}, where the exception message already names
     * them. They used to be a second {@code referencingBlockIds} key, which no contract declared and no
     * client read — an undeclared key is not an extension point, it is a shape only one implementation
     * knows about.
     */
    @ExceptionHandler(DocumentBlockReferencedException.class)
    public ResponseEntity<ErrorResponse> handleBlockReferenced(DocumentBlockReferencedException ex) {
        return error(HttpStatus.CONFLICT, "document.block.referenced", ex.getMessage());
    }

    /**
     * A stale metadata or content write. Core doesn't handle this one generically — both
     * {@code Document.lockVersion} and {@code DocumentDraft.lockVersion} are real
     * {@code @Version} fields, so Hibernate raises this on flush rather than the use case
     * throwing anything itself. The two locks are independent: editing metadata cannot make a
     * concurrent Tiptap autosave in some other locale fail.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleStaleWrite(OptimisticLockingFailureException ex) {
        return error(HttpStatus.CONFLICT, "document.stale-write", "This document was modified by someone else — reload and retry.");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String errorId, String errorText) {
        return ResponseEntity.status(status).body(new ErrorResponse(errorId, errorText));
    }
}
