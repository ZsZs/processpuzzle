package com.processpuzzle.baseentity.adapter.inbound.dto;

import com.processpuzzle.baseentity.model.BaseEntityDefinitionInput;
import com.processpuzzle.baseentity.model.EntityObjectInput;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultEntitiesDocumentTest {

    @Test
    void nullCollectionsDefaultToEmptyLists() {
        DefaultEntitiesDocument doc = new DefaultEntitiesDocument(null, null);

        assertThat(doc.entityDefinitions()).isNotNull().isEmpty();
        assertThat(doc.entities()).isNotNull().isEmpty();
    }

    @Test
    void nonNullCollectionsArePreserved() {
        BaseEntityDefinitionInput def = new BaseEntityDefinitionInput();
        def.setCode("test-def");
        EntityObjectInput entity = new EntityObjectInput();
        entity.setEntityDefinitionCode("test-def");

        DefaultEntitiesDocument doc = new DefaultEntitiesDocument(List.of(def), List.of(entity));

        assertThat(doc.entityDefinitions()).hasSize(1);
        assertThat(doc.entityDefinitions().get(0).getCode()).isEqualTo("test-def");
        assertThat(doc.entities()).hasSize(1);
        assertThat(doc.entities().get(0).getEntityDefinitionCode()).isEqualTo("test-def");
    }
}
