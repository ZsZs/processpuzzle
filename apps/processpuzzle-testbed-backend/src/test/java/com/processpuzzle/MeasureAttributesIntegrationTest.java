package com.processpuzzle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.baseentity.adapter.inbound.EntityDefinitionReleaseParticipant;
import com.processpuzzle.baseentity.api.EntityObjectAccess;
import com.processpuzzle.baseentity.api.EntityObjectView;
import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.definition.usecases.inbound.ImportEntityDefinitions;
import com.processpuzzle.baseentity.instances.usecases.inbound.CreateEntityInstanceUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.UpdateEntityInstanceUseCase;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The seeded measure attributes of the testbed through the real platform: the seed file's own definitions pass the
 * definition checks, its first sample instance passes the value checks, and what is stored carries the derived
 * numbers a query compares — read back equal, so saving an unchanged object does not bump its version.
 */
@SpringBootTest
class MeasureAttributesIntegrationTest {

    private static final String ORG = "measure-attributes";
    private static final String SEED = "default-entities/processpuzzle-testbed-entities.yaml";

    @Autowired private ImportEntityDefinitions importEntities;
    @Autowired private EntityDefinitionReleaseParticipant entityRelease;
    @Autowired private CreateEntityInstanceUseCase createEntity;
    @Autowired private UpdateEntityInstanceUseCase updateEntity;
    @Autowired private EntityObjectAccess entities;

    @BeforeEach
    void seed() throws IOException {
        String yaml = new ClassPathResource(SEED).getContentAsString(StandardCharsets.UTF_8);
        assertThat(importEntities.execute(ORG, new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))).errors()).isEmpty();
        entityRelease.release(ORG);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstSampleInstance() throws IOException {
        Map<String, Object> seed = new ObjectMapper(new YAMLFactory())
                .readValue(new ClassPathResource(SEED).getInputStream(), Map.class);
        Map<String, Object> first = ((List<Map<String, Object>>) seed.get("entities")).get(0);
        assertThat(first).containsEntry("entityDefinitionCode", "dynamic-entity");
        return new LinkedHashMap<>((Map<String, Object>) first.get("payload"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> measure(EntityObjectView object, String attribute) {
        return (Map<String, Object>) object.payload().get(attribute);
    }

    private static Map<String, Object> probe(Object weight, Object price, Object size) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "Probe");
        payload.put("weight", weight);
        payload.put("price", price);
        payload.put("size", size);
        return payload;
    }

    /** The orders and demo objects are seeded with quantities and prices of their own: the definitions take every one. */
    @Test
    @SuppressWarnings("unchecked")
    void everySeededOrderAndDemoObjectPassesItsDefinition() throws IOException {
        Map<String, Object> seed = new ObjectMapper(new YAMLFactory()).readValue(new ClassPathResource(SEED).getInputStream(), Map.class);
        List<Map<String, Object>> orders = ((List<Map<String, Object>>) seed.get("entities")).stream()
                .filter(sample -> List.of("order", "special-order", "demo-root").contains(sample.get("entityDefinitionCode"))).toList();
        assertThat(orders).hasSizeGreaterThanOrEqualTo(8);

        for (Map<String, Object> sample : orders) {
            String code = (String) sample.get("entityDefinitionCode");
            UUID id = createEntity.create(ORG, code, new LinkedHashMap<>((Map<String, Object>) sample.get("payload"))).getId();
            EntityObjectView stored = entities.find(ORG, code, id);
            if (!"demo-root".equals(code)) {
                assertThat(measure(stored, "total")).as("%s total", code).containsEntry("currency", "EUR");
                for (Map<String, Object> line : (List<Map<String, Object>>) stored.payload().get("lineItems")) {
                    assertThat((Map<String, Object>) line.get("quantity")).containsEntry("unit", "pc").containsKey("baseAmount");
                    assertThat((Map<String, Object>) line.get("unitPrice")).containsEntry("currency", "EUR").containsKey("baseAmount");
                }
            }
        }
    }

    @Test
    void theSeedsFirstSampleIsStoredWithItsDerivedNumbers() throws IOException {
        UUID id = createEntity.create(ORG, "dynamic-entity", firstSampleInstance()).getId();

        EntityObjectView stored = entities.find(ORG, "dynamic-entity", id);

        assertThat(measure(stored, "weight")).containsEntry("amount", "12.5").containsEntry("unit", "kg").containsEntry("baseAmount", 12.5);
        assertThat(measure(stored, "price")).containsEntry("amount", "1299.9").containsEntry("currency", "EUR").containsEntry("baseAmount", 1299.9);
        assertThat(measure(stored, "size")).containsEntry("unit", "cm").containsEntry("baseArea", 0.96).containsEntry("baseVolume", 0.48);
    }

    @Test
    void aValueInAnotherUnitIsStoredInCanonicalFormAndComparedInTheBaseUnit() {
        UUID id = createEntity.create(ORG, "dynamic-entity", probe(
                Map.of("amount", "250.0", "unit", "g"), Map.of("amount", "19.90", "currency", "HUF"),
                Map.of("width", "1.2", "height", "0.8", "depth", "0.5", "unit", "m"))).getId();

        EntityObjectView stored = entities.find(ORG, "dynamic-entity", id);

        assertThat(measure(stored, "weight")).containsEntry("amount", "250").containsEntry("baseAmount", 0.25);
        assertThat(measure(stored, "price")).containsEntry("baseAmount", 19.9);
        assertThat(measure(stored, "size")).containsEntry("baseArea", 0.96).containsEntry("baseVolume", 0.48);
    }

    @Test
    void whatTheDefinitionDoesNotAllowIsRefusedOnTheAttribute() {
        assertThatThrownBy(() -> createEntity.create(ORG, "dynamic-entity", probe(Map.of("amount", "1", "unit", "oz"), null, null)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getViolations())
                        .extracting("attributeCode", "message").containsExactly(tuple("weight", "unit 'oz' is not allowed here")));
        assertThatThrownBy(() -> createEntity.create(ORG, "dynamic-entity", probe(null, Map.of("amount", "1", "currency", "GBP"), null)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getViolations())
                        .extracting("attributeCode", "message").containsExactly(tuple("price", "currency 'GBP' is not allowed here")));
        assertThatThrownBy(() -> createEntity.create(ORG, "dynamic-entity", probe(null, null, Map.of("width", "1", "height", "2", "unit", "cm"))))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getViolations())
                        .extracting("attributeCode", "message").containsExactly(tuple("size", "depth must be a decimal number")));
        assertThatThrownBy(() -> createEntity.create(ORG, "dynamic-entity", probe(Map.of("amount", "1.2345", "unit", "kg"), null, null)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getViolations())
                        .extracting("message").containsExactly("amount has more than 3 decimal places"));
    }

    @Test
    void anUnchangedSaveKeepsTheVersionAndAChangedOneRecomputesWhatTheClientSentAsDerived() {
        UUID id = createEntity.create(ORG, "dynamic-entity", probe(Map.of("amount", "5", "unit", "kg"), null, null)).getId();
        EntityObjectView created = entities.find(ORG, "dynamic-entity", id);

        updateEntity.update(ORG, "dynamic-entity", id, created.version(), created.payload());
        EntityObjectView unchanged = entities.find(ORG, "dynamic-entity", id);
        assertThat(unchanged.version()).as("the stored payload is what a client sends back, and it is not a change").isEqualTo(created.version());

        Map<String, Object> edited = new LinkedHashMap<>(created.payload());
        edited.put("weight", Map.of("amount", "6", "unit", "kg", "baseAmount", 999.0));
        updateEntity.update(ORG, "dynamic-entity", id, unchanged.version(), edited);
        EntityObjectView changed = entities.find(ORG, "dynamic-entity", id);
        assertThat(changed.version()).isGreaterThan(unchanged.version());
        assertThat(measure(changed, "weight")).containsEntry("amount", "6").containsEntry("baseAmount", 6.0);
    }
}
