package com.processpuzzle;

import com.processpuzzle.baseentity.adapter.inbound.EntityDefinitionReleaseParticipant;
import com.processpuzzle.baseentity.api.EntityObjectAccess;
import com.processpuzzle.baseentity.api.EntityObjectAccessException;
import com.processpuzzle.baseentity.api.EntityObjectView;
import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.definition.usecases.inbound.ImportEntityDefinitions;
import com.processpuzzle.baseentity.instances.usecases.inbound.CreateEntityInstanceUseCase;
import com.processpuzzle.baseentity.unit.domain.OrganizationUnit;
import com.processpuzzle.baseentity.unit.domain.UnitStatus;
import com.processpuzzle.baseentity.unit.usecases.inbound.ManageOrganizationUnits;
import com.processpuzzle.rule.usecase.engine.RuleEngine;
import com.processpuzzle.rule.usecase.engine.RuleKey;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A unit an organization adds, through the real platform: a definition can name it, a value in it is stored with its base
 * amount, a retired unit still reads and writes, and what stored amounts depend on cannot change. Another organization has
 * not got the unit.
 */
@SpringBootTest
class OrganizationUnitsIntegrationTest {

    private static final String ORG = "org-units";
    private static final String OTHER_ORG = "org-units-other";

    private static final String ENTITIES = """
            entityDefinitions:
              - code: consignment
                name: Consignment
                status: ACTIVE
                isEmbedded: false
                componentParents: [ ]
                attributes:
                  - { code: reference, name: Reference, displayOrder: 1, valueKind: TEXT, formControlType: TEXT_BOX, required: true }
                  - { code: load, name: Load, displayOrder: 2, valueKind: REFERENCE, formControlType: QUANTITY, quantityKind: MASS, allowedUnits: [ pallet, kg ], defaultUnit: pallet }
            """;

    @Autowired private ManageOrganizationUnits units;
    @Autowired private ImportEntityDefinitions importEntities;
    @Autowired private EntityDefinitionReleaseParticipant entityRelease;
    @Autowired private CreateEntityInstanceUseCase createEntity;
    @Autowired private EntityObjectAccess entities;
    @Autowired private RuleEngine ruleEngine;

    private static OrganizationUnit pallet() {
        OrganizationUnit unit = new OrganizationUnit();
        unit.setCode("pallet");
        unit.setSymbol("plt");
        unit.setName("Pallet");
        unit.setKind("MASS");
        unit.setFactor(new BigDecimal("500"));
        unit.setDivisor(BigDecimal.ONE);
        return unit;
    }

    private List<String> importConsignment(String orgKey) throws IOException {
        return importEntities.execute(orgKey, new ByteArrayInputStream(ENTITIES.getBytes(StandardCharsets.UTF_8))).errors();
    }

    private long version(String org, UUID id) {
        return entities.find(org, "consignment", id).version();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(EntityObjectView consignment) {
        return (Map<String, Object>) consignment.payload().get("load");
    }

    @Test
    void aDefinitionCanNameAnOrganizationUnitOnlyOnceItExistsAndOnlyInThatOrganization() throws IOException {
        assertThat(importConsignment(ORG)).as("no pallet yet").isNotEmpty();

        units.create(ORG, pallet());

        assertThat(importConsignment(ORG)).isEmpty();
        assertThat(importConsignment(OTHER_ORG)).as("another organization has no pallet").isNotEmpty();
    }

    @Test
    void aValueInTheUnitIsStoredWithItsBaseAmountAndARetiredUnitStaysReadableAndWritable() throws IOException {
        String org = ORG + "-values";
        units.create(org, pallet());
        assertThat(importConsignment(org)).isEmpty();
        entityRelease.release(org);

        UUID id = createEntity.create(org, "consignment", Map.of("reference", "C-1", "load", Map.of("amount", "2", "unit", "pallet"))).getId();
        assertThat(load(entities.find(org, "consignment", id))).containsEntry("unit", "pallet").containsEntry("baseAmount", 1000.0);

        OrganizationUnit retire = pallet();
        retire.setStatus(UnitStatus.RETIRED);
        units.replace(org, "pallet", retire);

        assertThat(load(entities.find(org, "consignment", id))).as("what was stored stays").containsEntry("baseAmount", 1000.0);
        entities.updateAttributes(org, "consignment", id, Map.of("load", 3), version(org, id));
        assertThat(load(entities.find(org, "consignment", id))).as("a bare number is in the default unit, a retired one still").containsEntry("unit", "pallet").containsEntry("baseAmount", 1500.0);
        assertThatThrownBy(() -> entities.updateAttributes(org, "consignment", id, Map.of("load", Map.of("amount", "1", "unit", "crate")), version(org, id)))
            .isInstanceOf(EntityObjectAccessException.InvalidValue.class);
    }

    @Test
    void aRuleOfTheOrganizationReadsTheUnitAndAnotherOrganizationsRuleDoesNot() {
        String org = ORG + "-rules";
        String expression = "qty(entity.load).gt(qty('900 kg'))";
        Map<String, Object> entity = Map.of("load", Map.of("amount", "2", "unit", "pallet"));
        assertThatThrownBy(() -> ruleEngine.evaluate(RuleKey.of(org, "heavy"), expression, entity)).hasMessageContaining("pallet");

        units.create(org, pallet());

        assertThat(ruleEngine.evaluate(RuleKey.of(org, "heavy"), expression, entity)).as("2 pallets = 1000 kg").isTrue();
        assertThatThrownBy(() -> ruleEngine.evaluate(RuleKey.of(OTHER_ORG, "heavy"), expression, entity)).hasMessageContaining("pallet");
    }

    @Test
    void whatStoredAmountsAreDerivedFromCannotChange() {
        String org = ORG + "-frozen";
        units.create(org, pallet());
        OrganizationUnit heavier = pallet();
        heavier.setFactor(new BigDecimal("600"));

        assertThatThrownBy(() -> units.replace(org, "pallet", heavier)).isInstanceOf(ConflictException.class);
        assertThat(units.find(org, "pallet").getFactor()).isEqualByComparingTo("500");
    }
}
