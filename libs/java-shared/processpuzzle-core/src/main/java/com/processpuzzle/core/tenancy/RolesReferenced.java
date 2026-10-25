package com.processpuzzle.core.tenancy;

import java.util.List;
import java.util.Objects;

/**
 * Some feature stored a reference to realm roles — a document's editor roles, a navigation item's
 * visibility — so those roles have to exist in the organization's identity provider before anyone can
 * hold them.
 *
 * <p>Published as a Spring application event so that the feature naming a role never calls the one
 * that creates it. org-admin listens and creates the roles that are missing; nothing else is expected
 * to react. The event states a <em>need</em>, not a command: roles that already exist, reserved names
 * and an unreachable identity provider are the listener's business.
 *
 * @param orgKey      the organization whose realm must declare the roles
 * @param description what to tell an administrator who finds the role in the realm, for example
 *                    {@code "Referenced by documents"}
 * @param roles       realm role names; blanks and duplicates are tolerated and ignored
 */
public record RolesReferenced(String orgKey, String description, List<String> roles) {

    public RolesReferenced {
        roles = roles == null ? List.of() : roles.stream().filter(Objects::nonNull).toList();
    }
}
