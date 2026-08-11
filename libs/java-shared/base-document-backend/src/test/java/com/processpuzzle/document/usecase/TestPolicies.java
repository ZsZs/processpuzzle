package com.processpuzzle.document.usecase;

import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;

import java.util.Collection;
import java.util.List;

/**
 * The two access policies almost every use-case test needs. Kept next to {@link TestGuards} — that
 * one wires a policy into a {@code DocumentGuard}, this one supplies the policies to wire.
 */
final class TestPolicies {

    private TestPolicies() {
    }

    /** Every default is permitting, so this stands in for "an authenticated member with every role". */
    static DocumentAccessPolicy permitAll() {
        return new DocumentAccessPolicy() {
        };
    }

    /** A member of the organization holding exactly {@code roles} and nothing else. */
    static DocumentAccessPolicy holding(String... roles) {
        List<String> held = List.of(roles);
        return new DocumentAccessPolicy() {
            @Override
            public boolean hasAnyRole(Collection<String> requiredRoles) {
                return requiredRoles.stream().anyMatch(held::contains);
            }
        };
    }

    /** Not a member at all — {@code requireOrganizationAccess} rejects before anything else runs. */
    static DocumentAccessPolicy outsider() {
        return new DocumentAccessPolicy() {
            @Override
            public void requireAccess(String orgKey) {
                throw new IllegalStateException("The current principal is not a member of '" + orgKey + "'");
            }
        };
    }

    /** An anonymous caller: only {@code isPublic} content is readable. */
    static DocumentAccessPolicy anonymous() {
        return new DocumentAccessPolicy() {
            @Override
            public boolean isAuthenticated() {
                return false;
            }
        };
    }

    /** Named, so audit fields have something to record. */
    static DocumentAccessPolicy principal(String name) {
        return new DocumentAccessPolicy() {
            @Override
            public String currentPrincipal() {
                return name;
            }
        };
    }
}
