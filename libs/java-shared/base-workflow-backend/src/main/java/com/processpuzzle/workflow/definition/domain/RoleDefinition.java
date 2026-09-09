package com.processpuzzle.workflow.definition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A role tasks can be assigned to — a tenant-level catalog entity, not a child of one workflow.
 * The same role takes part in many {@link Workflow}s, each of which references it through a
 * {@link RoleUse} and pins it to a task through a {@link TaskUse}.
 *
 * <p>Composite key (orgKey, id) and the {@link com.processpuzzle.workflow.common.Auditable} /
 * {@link #version} pair mirror {@link ToolDefinition}, which has been a catalog aggregate all
 * along. There is deliberately no back-reference to a workflow: a role that knew its workflow
 * could only belong to one.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, of = {"orgKey", "id"})
@ToString
@Entity
@Table(name = "workflow_role_definition")
@IdClass(RoleDefinitionKey.class)
public class RoleDefinition extends com.processpuzzle.workflow.common.Auditable {

    @Id
    @Column(name = "org_key", nullable = false)
    private String orgKey;

    /** Role code, chosen by the author and unique per organization. */
    @Id
    @Column(nullable = false)
    private String id;

    @Column(nullable = false)
    private String name;

    private String description;

    /**
     * Ids of the {@link ArtifactDefinition}s this role owns the outcome of. Ownership, not access:
     * what a task reads or writes is stated on the task, in {@link TaskDefinition#getInputs()} /
     * {@link TaskDefinition#getOutputs()}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> responsibleFor = new ArrayList<>();

    /**
     * ID of the corresponding role definition in base-entity. When set, base-workflow validates
     * that a user holds this entity role before allowing them to be assigned to tasks that
     * require this role — see {@code RoleMembershipPort}.
     */
    private String entityRoleId;

    @Version
    private Long version;
}
