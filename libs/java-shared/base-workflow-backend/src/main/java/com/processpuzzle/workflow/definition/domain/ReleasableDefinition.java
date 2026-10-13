package com.processpuzzle.workflow.definition.domain;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.workflow.common.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/**
 * A workflow definition a designer authors and an organization releases: the workflow, its diagram and its catalogue
 * — roles, tasks, artifacts and tools. Each is stored twice, as the draft the designer edits and the row the
 * organization released, which is what the running application executes (see core's
 * {@code docs/definition-release.md}).
 *
 * <p>{@link #channel} is part of each subclass's key, the third field of its {@code @IdClass}. {@code @ColumnDefault}
 * so that {@code ddl-auto} can add the column to a table that already has rows; {@code WorkflowChannelBackfill} then
 * widens the key and gives each row its released copy.
 */
@Getter
@Setter
@MappedSuperclass
@EqualsAndHashCode(callSuper = false, of = "channel")
public abstract class ReleasableDefinition extends Auditable {

    @Id
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'DRAFT'")
    @Column(name = "channel", length = 16, nullable = false)
    private Channel channel = Channel.DRAFT;
}
