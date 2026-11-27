package com.processpuzzle.baseentity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.baseentity.adapter.inbound.dto.DefaultEntitiesDocument;
import com.processpuzzle.baseentity.definition.adapters.inbound.EntityDefinitionMapper;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A definition's fingerprint is the same whatever order its attributes are held in. A starter upgrade compares a hash
 * recorded from one load with a fingerprint of another, and a database does not return attributes in the order they
 * were written.
 */
class EntityDefinitionFingerprintOrderTest {

    private static final String DEFINITION = """
        entityDefinitions:
          - code: sailrc-sailing-club
            name: Sailing Club
            status: ACTIVE
            identifiers:
              - [name]
            layout:
              - [name, region]
            attributes:
              - code: name
                name: Name
                displayOrder: 1
                valueKind: TEXT
                formControlType: TEXT
                required: true
              - code: region
                name: Region
                displayOrder: 2
                valueKind: TEXT
                formControlType: TEXT
              - code: address
                name: Place
                displayOrder: 3
                valueKind: REFERENCE
                formControlType: ADDRESS
                defaultCountry: DE
        """;

    @Test
    void theOrderOfTheAttributesDoesNotChangeTheFingerprint() throws Exception {
        EntityDefinitionMapper mapper = new EntityDefinitionMapper();
        EntityDefinitionFingerprint fingerprint = new EntityDefinitionFingerprint(mapper);
        DefaultEntitiesDocument document = new ObjectMapper(new YAMLFactory())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(DEFINITION.getBytes(StandardCharsets.UTF_8), DefaultEntitiesDocument.class);
        BaseEntityDefinition inOrder = mapper.toDomain(document.entityDefinitions().getFirst());
        BaseEntityDefinition reversed = mapper.toDomain(document.entityDefinitions().getFirst());
        List<?> attributes = new ArrayList<>(reversed.getAttributes());
        Collections.reverse(attributes);
        reversed.getAttributes().clear();
        attributes.forEach(attribute -> reversed.getAttributes().add((com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute) attribute));

        assertThat(fingerprint.of(reversed)).isEqualTo(fingerprint.of(inOrder));
    }
}
