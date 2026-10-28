package com.processpuzzle.starter.bundle;

import com.processpuzzle.core.definition.DefinitionKinds;
import java.util.Map;
import java.util.Optional;

/**
 * The manifest's {@code contents} groups and the kind each one carries: the definition kinds, and the two kinds a
 * starter's modules and apps bring along, {@code translations} and {@code assets}.
 */
public final class ContentGroups {

    private static final Map<String, String> KIND_BY_GROUP = Map.ofEntries(
            Map.entry("entities", DefinitionKinds.ENTITY),
            Map.entry("states", DefinitionKinds.STATE),
            Map.entry("rules", DefinitionKinds.RULE),
            Map.entry("calculations", DefinitionKinds.CALCULATION),
            Map.entry("widgets", DefinitionKinds.WIDGET),
            Map.entry("documents", DefinitionKinds.DOCUMENT),
            Map.entry("workflows", DefinitionKinds.WORKFLOW),
            Map.entry("modules", DefinitionKinds.MODULE),
            Map.entry("apps", DefinitionKinds.APP),
            Map.entry("translations", DefinitionKinds.TRANSLATION),
            Map.entry("assets", DefinitionKinds.ASSET));

    private ContentGroups() {
    }

    /** The kind of {@code group}, or empty for a group the platform does not know. */
    public static Optional<String> kindOf(String group) {
        return Optional.ofNullable(KIND_BY_GROUP.get(group));
    }
}
