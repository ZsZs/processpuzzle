package com.processpuzzle.workflow.definition.domain;

import com.processpuzzle.core.definition.Channel;

/** Composite primary key of {@link TaskDefinition}; see {@link DefinitionKey}. */
public class TaskDefinitionKey extends DefinitionKey {

    private static final long serialVersionUID = 2L;

    public TaskDefinitionKey() {
        // required by JPA
    }

    public TaskDefinitionKey(String orgKey, String id, Channel channel) {
        super(orgKey, id, channel);
    }
}
