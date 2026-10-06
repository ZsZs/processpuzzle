package com.processpuzzle.starter.bundle;

import com.processpuzzle.core.definition.DefinitionKinds;
import java.util.Map;
import java.util.Optional;

/** The manifest's {@code contents} groups and the definition kind each one carries. */
public final class ContentGroups {

    private static final Map<String, String> KIND_BY_GROUP = Map.of(
            "entities", DefinitionKinds.ENTITY,
            "states", DefinitionKinds.STATE,
            "rules", DefinitionKinds.RULE,
            "widgets", DefinitionKinds.WIDGET,
            "documents", DefinitionKinds.DOCUMENT,
            "workflows", DefinitionKinds.WORKFLOW,
            "apps", DefinitionKinds.APP);

    private ContentGroups() {
    }

    /** The kind of {@code group}, or empty for a group the platform does not know. */
    public static Optional<String> kindOf(String group) {
        return Optional.ofNullable(KIND_BY_GROUP.get(group));
    }
}
