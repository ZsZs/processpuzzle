package com.processpuzzle.workflow.definition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A resource whose lifecycle is tracked across a process instance's tasks — as opposed to a plain
 * {@link TaskIOReference}, which base-workflow never tracks state for. {@code id} is unique only
 * within the owning {@link ProcessDefinition}.
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
    name = "workflow_work_product_definition",
    uniqueConstraints = @UniqueConstraint(columnNames = {"process_org_key", "process_id", "id"}))
public class WorkProductDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "technical_id")
    private UUID technicalId;

    @Column(nullable = false)
    private String id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkProductType type;

    /** ID of the entity type in base-entity that backs this work product's data. */
    private String entityTypeId;

    /**
     * ID of the state machine definition in base-state that governs this work product's
     * lifecycle. Passed when publishing WorkProductInstanceCreatedEvent so base-state can
     * initialize the right machine.
     */
    private String stateMachineId;

    @ManyToOne
    @JoinColumns({
        @JoinColumn(name = "process_org_key", referencedColumnName = "org_key"),
        @JoinColumn(name = "process_id", referencedColumnName = "id")
    })
    private ProcessDefinition process;
}
