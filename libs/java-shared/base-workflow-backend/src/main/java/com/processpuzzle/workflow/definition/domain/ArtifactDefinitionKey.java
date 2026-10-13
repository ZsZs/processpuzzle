package com.processpuzzle.workflow.definition.domain;

import com.processpuzzle.core.definition.Channel;

/** Composite primary key of {@link ArtifactDefinition}; see {@link DefinitionKey}. */
public class ArtifactDefinitionKey extends DefinitionKey {

    private static final long serialVersionUID = 2L;

    public ArtifactDefinitionKey() {
        // required by JPA
    }

    public ArtifactDefinitionKey(String orgKey, String id, Channel channel) {
        super(orgKey, id, channel);
    }
}
