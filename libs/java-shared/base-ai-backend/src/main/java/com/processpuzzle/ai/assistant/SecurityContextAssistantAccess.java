package com.processpuzzle.ai.assistant;

import com.processpuzzle.core.security.CurrentPrincipal;

/**
 * The default {@link AssistantAccess}, read from the request's security context through {@link CurrentPrincipal}:
 * the organization is the realm the caller's token was issued by, and the caller may design only with the
 * {@value #DESIGNER_ROLE} realm role.
 *
 * <p>An application that authorizes the assistant some other way declares its own {@link AssistantAccess} bean, and
 * this one steps aside.
 */
public class SecurityContextAssistantAccess implements AssistantAccess {

    /** The realm role that lets a person design with the assistant; see docs/ai/implementation-plan.md. */
    public static final String DESIGNER_ROLE = "definition-designer";

    private final CurrentPrincipal principal;

    public SecurityContextAssistantAccess(CurrentPrincipal principal) {
        this.principal = principal;
    }

    /** The organization of the caller's token; empty when no token is present, so the turn cannot start. */
    @Override
    public String currentOrganization() {
        return principal.realm().orElse("");
    }

    @Override
    public boolean mayDesign() {
        return !currentOrganization().isEmpty() && principal.authorities().contains(DESIGNER_ROLE);
    }

    /** The caller's display name from the token, for the audit trail; empty when unauthenticated. */
    @Override
    public String principal() {
        return principal.displayName().orElse("");
    }
}
