package com.processpuzzle.workflow.definition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

/**
 * A role a process definition can assign tasks to. {@code id} is unique only within the owning
 * {@link ProcessDefinition} — not a JPA identity concern here, since a role never has an identity
 * apart from its process (see {@link ProcessDefinition#addRole}). Not addressable independently:
 * the roles sub-resource endpoints always resolve through the owning process aggregate.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, of = "technicalId")
@ToString(exclude = "process")
@Entity
@Table(
    name = "workflow_role_definition",
    uniqueConstraints = @UniqueConstraint(columnNames = {"process_technical_id", "id"}))
public class RoleDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "technical_id")
    private UUID technicalId;

    /** Role code, unique within the owning process definition. */
    @Column(nullable = false)
    private String id;

    @Column(nullable = false)
    private String name;

    private String description;

    /**
     * ID of the corresponding role definition in base-entity. When set, base-workflow validates
     * that a user holds this entity role before allowing them to be assigned to tasks that
     * require this role — see {@code RoleMembershipPort}.
     */
    private String entityRoleId;

    @ManyToOne
    @JoinColumn(name = "process_technical_id")
    private ProcessDefinition process;
}
