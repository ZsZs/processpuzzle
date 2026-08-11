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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Same shape as {@code RuleApiExceptionHandler} — module-specific exceptions only.
 * {@code IllegalArgumentException} (referential-integrity failures raised as 400) and
 * {@code IllegalStateException} are already handled generically by core's
 * {@code ApiExceptionHandler}, so nothing here duplicates those.
 *
 * <p>Note the response body key is {@code error}, matching the actual convention every
 * existing handler in this codebase uses — not {@code errorId}/{@code errorText} as
 * base-document-api.yaml's {@code ErrorResponse} schema currently documents. That schema
 * and the real response body have drifted apart across the whole codebase, not just here;
 * worth reconciling one way or the other rather than this module inventing a third shape.
 */
@RestControllerAdvice
public class DocumentApiExceptionHandler {

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(DocumentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DocumentBlockNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleBlockNotFound(DocumentBlockNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DocumentTranslationNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleTranslationNotFound(DocumentTranslationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DocumentAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleConflict(DocumentAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DocumentSlugAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleSlugConflict(DocumentSlugAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DocumentTranslationAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleTranslationConflict(DocumentTranslationAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DocumentPublishingConflictException.class)
    public ResponseEntity<Map<String, String>> handlePublishingConflict(DocumentPublishingConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    /**
     * 403 — and note what does <em>not</em> arrive here: a reader who may not see a document at all
     * gets {@link DocumentNotFoundException} instead, so 404 above. See
     * {@link DocumentAccessDeniedException} for why the two cases answer differently.
     */
    @ExceptionHandler(DocumentAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(DocumentAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DocumentBlockReferencedException.class)
    public ResponseEntity<Map<String, Object>> handleBlockReferenced(DocumentBlockReferencedException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        body.put("referencingBlockIds", ex.getReferencingBlockIds());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * A stale metadata or content write. Core doesn't handle this one generically — both
     * {@code Document.lockVersion} and {@code DocumentDraft.lockVersion} are real
     * {@code @Version} fields, so Hibernate raises this on flush rather than the use case
     * throwing anything itself. The two locks are independent: editing metadata cannot make a
     * concurrent Tiptap autosave in some other locale fail.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleStaleWrite(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "This document was modified by someone else — reload and retry."));
    }
}
