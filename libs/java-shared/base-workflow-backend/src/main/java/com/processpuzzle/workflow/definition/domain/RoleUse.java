package com.processpuzzle.workflow.definition.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A {@link RoleDefinition} taking part in one {@link Workflow}.
 *
 * <p>An object around a single id rather than the bare id itself, and deliberately: a use is where
 * configuration true of a definition <em>only in this workflow</em> belongs — narrowed eligibility,
 * for instance — so adding such a field later does not change the shape of
 * {@link Workflow#getRoles()}. The same reasoning gives {@link ArtifactUse} and {@link ToolUse}
 * their shape, and {@link TaskUse} is what one looks like once it has grown those fields.
 *
 * <p>Not a JPA entity, for the reason {@link TaskUse} is not: the list is stored as a single JSONB
 * column, so a no-arg-constructible getter/setter POJO is all Jackson needs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleUse {

    /** Id of a {@link RoleDefinition} of the same organization. */
    private String roleDefinitionId;
}
