package com.processpuzzle.workflow.definition.domain;

import com.processpuzzle.core.definition.Channel;

/** Composite primary key of {@link RoleDefinition}; see {@link DefinitionKey}. */
public class RoleDefinitionKey extends DefinitionKey {

    private static final long serialVersionUID = 2L;

    public RoleDefinitionKey() {
        // required by JPA
    }

    public RoleDefinitionKey(String orgKey, String id, Channel channel) {
        super(orgKey, id, channel);
    }
}
