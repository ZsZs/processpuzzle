package com.processpuzzle.workflow.definition.domain;

import com.processpuzzle.core.definition.Channel;

/** Composite primary key of {@link Workflow}; see {@link DefinitionKey}. */
public class WorkflowKey extends DefinitionKey {

    private static final long serialVersionUID = 2L;

    public WorkflowKey() {
        // required by JPA
    }

    public WorkflowKey(String orgKey, String id, Channel channel) {
        super(orgKey, id, channel);
    }
}
