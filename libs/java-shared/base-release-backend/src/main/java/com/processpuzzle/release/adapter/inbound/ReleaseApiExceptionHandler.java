package com.processpuzzle.release.adapter.inbound;

import com.processpuzzle.core.exception.ApiAdviceOrder;
import com.processpuzzle.release.model.PendingRelease;
import com.processpuzzle.release.usecase.ManagePendingSteps;
import com.processpuzzle.release.usecase.ReleaseRefusedException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers a refused release with 409 and the pending state that refused it, as base-release-api.yaml
 * declares. Scoped to this feature's package and ordered ahead of core's catch-all — see
 * {@code ApiAdviceOrder} for why both matter.
 */
@RestControllerAdvice(basePackages = "com.processpuzzle.release")
@Order(ApiAdviceOrder.FEATURE)
public class ReleaseApiExceptionHandler {

    private final ReleaseMapper mapper;

    public ReleaseApiExceptionHandler(ReleaseMapper mapper) {
        this.mapper = mapper;
    }

    @ExceptionHandler(ManagePendingSteps.UnknownMigrationStepException.class)
    public ResponseEntity<ProblemDetail> handleUnknownStep(ManagePendingSteps.UnknownMigrationStepException ex) {
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(ReleaseRefusedException.class)
    public ResponseEntity<PendingRelease> handleRefused(ReleaseRefusedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(mapper.toModel(ex.getPending()));
    }
}
