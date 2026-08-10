package com.processpuzzle.artifact.adapter.inbound;

import com.processpuzzle.artifact.usecase.exception.ArtifactAlreadyExistsException;
import com.processpuzzle.artifact.usecase.exception.ArtifactBlockNotFoundException;
import com.processpuzzle.artifact.usecase.exception.ArtifactBlockReferencedException;
import com.processpuzzle.artifact.usecase.exception.ArtifactNotFoundException;
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
 * base-artifact-api.yaml's {@code ErrorResponse} schema currently documents. That schema
 * and the real response body have drifted apart across the whole codebase, not just here;
 * worth reconciling one way or the other rather than this module inventing a third shape.
 */
@RestControllerAdvice
public class ArtifactApiExceptionHandler {

    @ExceptionHandler(ArtifactNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ArtifactNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ArtifactBlockNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleBlockNotFound(ArtifactBlockNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ArtifactAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleConflict(ArtifactAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ArtifactBlockReferencedException.class)
    public ResponseEntity<Map<String, Object>> handleBlockReferenced(ArtifactBlockReferencedException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        body.put("referencingBlockIds", ex.getReferencingBlockIds());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * A stale {@code updateArtifact}/block write. Core doesn't handle this one generically —
     * {@code Artifact.version} is a real {@code @Version}, so Hibernate raises this on flush
     * rather than the use case throwing anything itself.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleStaleWrite(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "This artifact was modified by someone else — reload and retry."));
    }
}
