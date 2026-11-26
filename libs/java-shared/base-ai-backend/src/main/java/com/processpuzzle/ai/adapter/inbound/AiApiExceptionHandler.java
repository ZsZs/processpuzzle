package com.processpuzzle.ai.adapter.inbound;

import com.processpuzzle.ai.assist.AssistUnavailableException;
import com.processpuzzle.ai.assist.UnknownAssistTaskException;
import com.processpuzzle.ai.assistant.AssistantNotConfiguredException;
import com.processpuzzle.ai.assistant.AssistantQuotaExceededException;
import com.processpuzzle.ai.usecase.exception.AiRequestException;
import com.processpuzzle.ai.usecase.port.MediaStoreUnavailableException;
import com.processpuzzle.core.exception.ApiAdviceOrder;
import com.processpuzzle.shared.model.ErrorResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps this feature's refusals onto the {@code ErrorResponse} every 4xx of ai-api.yaml declares. Only
 * this feature's own exception types, scoped to its package and ordered ahead of core's catch-all —
 * see base-widget's handler for why each of the three matters.
 */
@RestControllerAdvice(basePackages = "com.processpuzzle.ai")
@Order(ApiAdviceOrder.FEATURE)
public class AiApiExceptionHandler {

    @ExceptionHandler(AiRequestException.class)
    public ResponseEntity<ErrorResponse> handleRefusal(AiRequestException ex) {
        HttpStatus status = switch (ex.getKind()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID -> HttpStatus.BAD_REQUEST;
            case CONFLICT -> HttpStatus.CONFLICT;
            case TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(ex.getErrorId(), ex.getMessage()));
    }

    @ExceptionHandler(MediaStoreUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleStorageUnavailable(MediaStoreUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("ai.media.storage-unavailable", ex.getMessage()));
    }

    @ExceptionHandler(UnknownAssistTaskException.class)
    public ResponseEntity<ErrorResponse> handleUnknownAssistTask(UnknownAssistTaskException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("ai.assist.unknown-task", ex.getMessage()));
    }

    @ExceptionHandler(AssistantNotConfiguredException.class)
    public ResponseEntity<ErrorResponse> handleNotConfigured(AssistantNotConfiguredException ex) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                .body(new ErrorResponse("ai.assist.not-configured", ex.getMessage()));
    }

    @ExceptionHandler(AssistantQuotaExceededException.class)
    public ResponseEntity<ErrorResponse> handleQuotaExceeded(AssistantQuotaExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse("ai.assist.quota-exceeded", ex.getMessage()));
    }

    @ExceptionHandler(AssistUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleModelUnavailable(AssistUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse("ai.assist.model-unavailable", ex.getMessage()));
    }
}
