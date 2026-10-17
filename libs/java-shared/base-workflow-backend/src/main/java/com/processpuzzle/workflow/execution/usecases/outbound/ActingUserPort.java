package com.processpuzzle.workflow.execution.usecases.outbound;

import java.util.Optional;

/**
 * Who is making the current request — for stamping what they did with their id and name.
 *
 * <p>base-workflow does not read tokens: which security stack carries the caller is the host application's
 * business, the same reason {@link RoleMembershipPort} is a port. Without an implementation every request is
 * anonymous, so a comment is attributed to an unknown author and a self-claimed task records no name.
 */
public interface ActingUserPort {

    /** Empty when the request is anonymous. */
    Optional<String> userId();

    /** The name people know the caller by; empty when anonymous or when the token names nobody. */
    Optional<String> displayName();
}
