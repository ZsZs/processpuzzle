package com.processpuzzle.rule.adapter.inbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.processpuzzle.rule.usecase.exception.RuleAlreadyExistsException;
import com.processpuzzle.rule.usecase.exception.RuleNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RuleNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(RuleNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(RuleAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleConflict(RuleAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflictState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<Map<String, String>> handleParseError(JsonProcessingException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Could not parse YAML: " + ex.getOriginalMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.badRequest().body(fieldErrors);
    }
}
