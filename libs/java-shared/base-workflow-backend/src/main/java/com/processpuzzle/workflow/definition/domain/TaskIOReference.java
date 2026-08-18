package com.processpuzzle.workflow.definition.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single input/output reference of a {@link TaskDefinition}: points directly at a base-entity
 * entity, a base-artifact document, or a widget — not at a {@link WorkProductDefinition}. Use
 * work products when the resource's lifecycle must be tracked by base-state; use these references
 * for plain read/write access.
 *
 * <p>Not a JPA entity: {@link TaskDefinition#getInputs()} / {@link TaskDefinition#getOutputs()}
 * store lists of these as a single JSONB column (Jackson-serialized via
 * {@code @JdbcTypeCode(SqlTypes.JSON)}), the same way {@code BaseEntityDefinition.componentParents}
 * stores a {@code List<String>}. A plain no-arg-constructible, getter/setter POJO is all Jackson
 * needs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskIOReference {
    private ReferenceType type;
    private String refId;
    private String label;
}
