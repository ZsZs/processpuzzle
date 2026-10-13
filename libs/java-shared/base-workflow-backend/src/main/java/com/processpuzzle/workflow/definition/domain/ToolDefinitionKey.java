package com.processpuzzle.workflow.definition.domain;

import com.processpuzzle.core.definition.Channel;

/** Composite primary key of {@link ToolDefinition}; see {@link DefinitionKey}. */
public class ToolDefinitionKey extends DefinitionKey {

    private static final long serialVersionUID = 2L;

    public ToolDefinitionKey() {
        // required by JPA
    }

    public ToolDefinitionKey(String orgKey, String id, Channel channel) {
        super(orgKey, id, channel);
    }
}
