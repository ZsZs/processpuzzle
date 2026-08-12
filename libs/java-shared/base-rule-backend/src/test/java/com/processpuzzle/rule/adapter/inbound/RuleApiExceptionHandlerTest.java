package com.processpuzzle.rule.adapter.inbound;

import com.processpuzzle.rule.usecase.exception.RuleAlreadyExistsException;
import com.processpuzzle.rule.usecase.exception.RuleNotFoundException;
import com.processpuzzle.shared.model.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class RuleApiExceptionHandlerTest {

    private final RuleApiExceptionHandler handler = new RuleApiExceptionHandler();

    @Test
    void anUnknownRuleBecomes404() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(new RuleNotFoundException("demo", "missing"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getErrorId()).isEqualTo("rule.not-found");
        assertThat(response.getBody().getErrorText()).isEqualTo("Rule not found: demo/missing");
    }

    @Test
    void aDuplicateRuleIdBecomes409() {
        ResponseEntity<ErrorResponse> response = handler.handleConflict(new RuleAlreadyExistsException("demo", "max-quantity"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getErrorId()).isEqualTo("rule.already-exists");
        assertThat(response.getBody().getErrorText()).isEqualTo("Rule already exists: demo/max-quantity");
    }
}
