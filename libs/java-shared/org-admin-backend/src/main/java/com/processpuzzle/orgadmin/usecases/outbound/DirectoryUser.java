package com.processpuzzle.orgadmin.usecases.outbound;

import java.time.Instant;
import java.util.List;

/**
 * A user as the identity provider holds it.
 *
 * <p>A record in this module's own vocabulary rather than the generated
 * {@code orgadmin.model.OrganizationUser}: the port is what a substitute directory implements, and
 * making an implementor depend on a generated contract type would tie every future adapter to this
 * one HTTP shape.
 *
 * @param id the provider's own opaque id — not a ProcessPuzzle id, and not the username
 * @param enabled a disabled user keeps their account and roles but cannot obtain a token
 * @param createdAt when the provider created the user; may be {@code null} if it does not report one
 * @param roles realm roles currently granted
 */
public record DirectoryUser(String id, String username, String email, String firstName,
                            String lastName, boolean enabled, boolean emailVerified,
                            Instant createdAt, List<String> roles) {

    public DirectoryUser {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
