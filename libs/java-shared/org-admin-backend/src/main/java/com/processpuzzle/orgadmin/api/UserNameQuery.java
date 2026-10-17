package com.processpuzzle.orgadmin.api;

import java.util.Optional;

/**
 * The name a tenant's user is known by — for a screen that records who did something beside the user's id, such
 * as base-workflow's task assignee.
 *
 * <p>Best effort: empty when the directory does not know the user, knows no name, or cannot be reached. A caller
 * records the id regardless, so a missing name is never a reason to fail what it is doing.
 */
public interface UserNameQuery {

    Optional<String> displayNameOf(String orgKey, String userId);
}
