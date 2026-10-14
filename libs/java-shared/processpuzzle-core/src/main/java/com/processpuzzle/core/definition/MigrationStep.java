package com.processpuzzle.core.definition;

import java.util.Map;

/**
 * One change a release makes to existing objects so that they fit the definitions it releases: the attribute renamed,
 * the empty values filled, the old values mapped onto new ones. A designer adds a step to resolve a
 * {@link ReleaseProblem}; the release runs every step first, inside its own transaction, and then checks the data as
 * the steps left it.
 *
 * @param id         stable within the organization's pending steps, for reports and for removing a step
 * @param kind       the kind of the definition whose objects the step changes, as {@link ReleaseProblem#kind()}
 * @param key        the key of that definition
 * @param operation  what the step does; the {@link MigrationParticipant} of {@code kind} names the operations it runs
 * @param subject    the part of the definition the step is about — an attribute's code — as
 *                   {@link ReleaseProblem#subject()}
 * @param parameters what the operation needs besides its subject, as JSON values
 */
public record MigrationStep(String id, String kind, String key, String operation, String subject,
                            Map<String, Object> parameters) {

    public MigrationStep {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
