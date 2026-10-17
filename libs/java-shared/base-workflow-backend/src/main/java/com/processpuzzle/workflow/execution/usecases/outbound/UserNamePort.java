package com.processpuzzle.workflow.execution.usecases.outbound;

import java.util.Optional;

/**
 * The name people know a user by, for recording beside the user's id — {@code TaskInstance.assigneeName}.
 *
 * <p>base-workflow keeps no user directory, so the host application provides this, typically over its identity
 * provider; see {@code processpuzzle-testbed-backend}'s adapter over org-admin's directory. Without one, a user
 * assigning a task to themselves is still named from their own token — see {@code AssignTaskUseCase}.
 */
public interface UserNamePort {

    /** Empty when the directory does not know the user, or knows no name for them. */
    Optional<String> displayNameOf(String orgKey, String userId);
}
