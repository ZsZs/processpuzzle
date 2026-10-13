package com.processpuzzle.workflow.definition.adapters.inbound.release;

import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.workflow.definition.usecases.inbound.ImportOutcome;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Releases what a workflow import wrote, and nothing else — for a loader that seeds a vetted set the running
 * application is meant to have, without releasing a designer's unrelated drafts. In release order: the catalogue
 * before the workflows that use it.
 */
@Component
public class ReleaseImportedWorkflows {

    private final ArtifactReleaseParticipant artifacts;
    private final RoleReleaseParticipant roles;
    private final ToolReleaseParticipant tools;
    private final TaskReleaseParticipant tasks;
    private final WorkflowReleaseParticipant workflows;

    public ReleaseImportedWorkflows(ArtifactReleaseParticipant artifacts, RoleReleaseParticipant roles,
                                    ToolReleaseParticipant tools, TaskReleaseParticipant tasks,
                                    WorkflowReleaseParticipant workflows) {
        this.artifacts = artifacts;
        this.roles = roles;
        this.tools = tools;
        this.tasks = tasks;
        this.workflows = workflows;
    }

    @Transactional
    public void release(String orgKey, ImportOutcome outcome) {
        artifacts.release(orgKey, outcome.written(DefinitionKinds.ARTIFACT));
        roles.release(orgKey, outcome.written(DefinitionKinds.ROLE));
        tools.release(orgKey, outcome.written(DefinitionKinds.TOOL));
        tasks.release(orgKey, outcome.written(DefinitionKinds.TASK));
        workflows.release(orgKey, outcome.written(DefinitionKinds.WORKFLOW));
    }
}
