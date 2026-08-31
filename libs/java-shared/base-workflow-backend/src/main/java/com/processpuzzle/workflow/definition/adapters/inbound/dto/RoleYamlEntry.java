package com.processpuzzle.workflow.definition.adapters.inbound.dto;

import java.util.List;

/** One role definition of a {@link WorkflowYamlDocument}'s {@code role-definitions} section. */
public record RoleYamlEntry(
        String id,
        String name,
        String description,
        List<String> responsibleFor,
        String entityRoleId
) {
}
