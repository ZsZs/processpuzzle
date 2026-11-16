package com.processpuzzle.chat.common;

import com.processpuzzle.chat.conversation.usecases.exception.ChatAccessDeniedException;
import com.processpuzzle.chat.conversation.usecases.exception.ChatRateLimitedException;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationConflictException;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotFoundException;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotWritableException;
import com.processpuzzle.chat.conversation.usecases.exception.InvalidChatRequestException;
import com.processpuzzle.chat.definition.usecases.exception.ChatDefinitionNotFoundException;
import com.processpuzzle.chat.definition.usecases.exception.InvalidChatDefinitionException;
import com.processpuzzle.chat.definition.usecases.exception.InvalidRevisionException;
import com.processpuzzle.chat.definition.usecases.exception.StaleChatDefinitionException;
import com.processpuzzle.core.exception.ApiAdviceOrder;
import com.processpuzzle.shared.model.ErrorResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps base-chat's own exceptions to the {@code errorId}/{@code errorText} shape. Scoped to this module's
 * package and on the {@link ApiAdviceOrder#FEATURE} rung, so it neither shadows nor is shadowed by another
 * feature's advice claiming the same Spring exception.
 *
 * <p>404 means "not yours to know" as well as "not there"; 403 only "you may look but not do this". The
 * messages never name a subject or a conversation the caller may not read.
 */
@RestControllerAdvice(basePackages = "com.processpuzzle.chat")
@Order(ApiAdviceOrder.FEATURE)
public class ChatApiExceptionHandler {

    @ExceptionHandler(ConversationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleConversationNotFound(ConversationNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "chat.conversation.not-found", e.getMessage());
    }

    @ExceptionHandler(ChatDefinitionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDefinitionNotFound(ChatDefinitionNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "chat.definition.not-found", e.getMessage());
    }

    @ExceptionHandler(ChatAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(ChatAccessDeniedException e) {
        return error(HttpStatus.FORBIDDEN, "chat.access-denied", e.getMessage());
    }

    @ExceptionHandler(ConversationNotWritableException.class)
    public ResponseEntity<ErrorResponse> handleNotWritable(ConversationNotWritableException e) {
        return error(HttpStatus.CONFLICT, "chat.conversation.not-writable", e.getMessage());
    }

    @ExceptionHandler(ChatRateLimitedException.class)
    public ResponseEntity<ErrorResponse> handleRateLimited(ChatRateLimitedException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, (e.getRetryAfter().toMillis() + 999) / 1000)))
                .body(new ErrorResponse("chat.rate-limited", e.getMessage()));
    }

    @ExceptionHandler(ConversationConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConversationConflictException e) {
        return error(HttpStatus.CONFLICT, "chat.conversation.conflict", e.getMessage());
    }

    @ExceptionHandler({StaleChatDefinitionException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> handleStale(RuntimeException e) {
        return error(HttpStatus.CONFLICT, "chat.definition.stale-write", e.getMessage());
    }

    @ExceptionHandler(InvalidChatDefinitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDefinition(InvalidChatDefinitionException e) {
        return error(HttpStatus.BAD_REQUEST, "chat.definition.invalid", e.getMessage());
    }

    @ExceptionHandler(InvalidRevisionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRevision(InvalidRevisionException e) {
        return error(HttpStatus.BAD_REQUEST, "chat.definition.invalid-revision", e.getMessage());
    }

    @ExceptionHandler(InvalidChatRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidChatRequestException e) {
        return error(HttpStatus.BAD_REQUEST, "chat.invalid-request", e.getMessage());
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String errorId, String errorText) {
        return ResponseEntity.status(status).body(new ErrorResponse(errorId, errorText));
    }
}
