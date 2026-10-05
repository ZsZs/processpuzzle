package com.processpuzzle.starter.adapter.inbound;

import com.processpuzzle.core.exception.ApiAdviceOrder;
import com.processpuzzle.starter.model.ImportReport;
import com.processpuzzle.starter.usecase.ImportRejectedException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers a refused import with its {@code ImportReport}, as base-starter-api.yaml declares: 413 for a
 * bundle over a size limit, 422 for everything else. Scoped to this feature's package and ordered ahead
 * of core's catch-all — see {@code ApiAdviceOrder} for why both matter.
 */
@RestControllerAdvice(basePackages = "com.processpuzzle.starter")
@Order(ApiAdviceOrder.FEATURE)
public class StarterApiExceptionHandler {

    private final StarterMapper mapper;

    public StarterApiExceptionHandler(StarterMapper mapper) {
        this.mapper = mapper;
    }

    @ExceptionHandler(ImportRejectedException.class)
    public ResponseEntity<ImportReport> handleRejected(ImportRejectedException ex) {
        HttpStatus status = ex.isTooLarge() ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(mapper.toModel(ex.getReport()));
    }
}
