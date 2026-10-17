package com.processpuzzle.security;

import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.workflow.execution.usecases.outbound.ActingUserPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * base-workflow's {@link ActingUserPort} read off the bearer token: its subject as the user id, and the name
 * {@link CurrentPrincipal#displayName()} picks — so a task comment and a self-claimed task carry who did it.
 */
@Component
public class TokenActingUser implements ActingUserPort {

    private final CurrentPrincipal principal;

    public TokenActingUser(CurrentPrincipal principal) {
        this.principal = principal;
    }

    @Override
    public Optional<String> userId() {
        return principal.subject();
    }

    @Override
    public Optional<String> displayName() {
        return principal.displayName();
    }
}
