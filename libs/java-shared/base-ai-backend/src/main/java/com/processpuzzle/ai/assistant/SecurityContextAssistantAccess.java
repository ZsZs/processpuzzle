package com.processpuzzle.ai.assistant;

import com.processpuzzle.core.security.CurrentPrincipal;
import java.util.Set;

/**
 * The default {@link AssistantAccess}, read from the request's security context through {@link CurrentPrincipal}:
 * the organization is the one the caller's token names in its {@code organization} claim, or the realm the token was
 * issued by when the claim is absent (one realm per tenant). A token naming several organizations is no single
 * organization, so the caller acts for none. The caller may design only with the {@value #DESIGNER_ROLE} realm role.
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

    /**
     * The organization of the caller's token; empty when no token is present, or when it names several organizations,
     * so the turn cannot start. In a shared realm the token names the organization under the alias the URL carries.
     */
    @Override
    public String currentOrganization() {
        Set<String> organizations = principal.organizations();
        if (organizations.isEmpty()) {
            return principal.realm().orElse("");
        }
        return organizations.size() == 1 ? organizations.iterator().next() : "";
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

    /** The token's subject, the designer's stable identifier; empty when unauthenticated. */
    @Override
    public String subject() {
        return principal.subject().orElse("");
    }
}
