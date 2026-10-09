package com.processpuzzle.workflow.execution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The last {@link WorkflowInstance#getInstanceNumber() instance number} handed out in one organization.
 * One row per organization, read under a pessimistic write lock so two concurrent starts cannot draw the
 * same number — a row lock rather than a database sequence because a sequence is per database, not per
 * organization, and works the same on PostgreSQL and the H2 the tests run on.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "workflow_instance_counter")
public class WorkflowInstanceCounter {

    @Id
    @Column(name = "org_key")
    private String orgKey;

    @Column(nullable = false)
    private long lastNumber;
}
