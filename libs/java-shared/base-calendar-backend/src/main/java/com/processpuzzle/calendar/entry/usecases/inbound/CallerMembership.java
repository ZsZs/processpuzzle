package com.processpuzzle.calendar.entry.usecases.inbound;

import com.processpuzzle.core.security.CurrentPrincipal;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Who the caller is to one organization, for the parts of a calendar that depend on people rather than on rules:
 * invitations by name, invitations by role, and what a directory search may return.
 *
 * <p>A member is a signed-in caller whose token comes from the organization's realm, or platform staff. Only a member's
 * realm roles count for an invitation: a role of another organization's realm means nothing here. Anybody else has
 * no subject-based standing at all.
 */
@Component
public class CallerMembership {

    private final CurrentPrincipal principal;

    public CallerMembership(CurrentPrincipal principal) {
        this.principal = principal;
    }

    /** The caller's subject, when the caller is a member of the organization. */
    public Optional<String> memberSubject(String orgKey) {
        return isMember(orgKey) ? principal.subject() : Optional.empty();
    }

    /** The caller's realm roles, when the caller is a member of the organization; none otherwise. */
    public Set<String> memberRoles(String orgKey) {
        return isMember(orgKey) ? principal.authorities() : Set.of();
    }

    public boolean isMember(String orgKey) {
        return principal.isAuthenticated() && (principal.isPlatformAdmin() || principal.isMemberOf(orgKey));
    }
}
