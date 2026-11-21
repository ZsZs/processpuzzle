package com.processpuzzle;

import com.processpuzzle.baseentity.adapter.inbound.EntityDefinitionReleaseParticipant;
import com.processpuzzle.baseentity.api.EntityObjectAccess;
import com.processpuzzle.baseentity.api.EntityObjectView;
import com.processpuzzle.baseentity.definition.usecases.inbound.ImportEntityDefinitions;
import com.processpuzzle.baseentity.instances.usecases.inbound.CreateEntityInstanceUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.UpdateEntityInstanceUseCase;
import com.processpuzzle.calculation.adapter.inbound.CalculationReleaseParticipant;
import com.processpuzzle.calculation.domain.CalculationRun;
import com.processpuzzle.calculation.domain.RunStatus;
import com.processpuzzle.calculation.usecase.FindCalculationRuns;
import com.processpuzzle.calculation.usecase.ImportCalculationDefinitions;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A calculation that writes into quantity and money attributes, through the real platform: it sums the embedded rows' weights
 * (read in the base unit, so grams and kilograms add up) and prices, and writes bare numbers, which base-entity takes to be in
 * the attribute's default unit and currency, rounds to its decimal places and stores with the numbers a query compares.
 */
@SpringBootTest
class MeasureCalculationIntegrationTest {

    private static final String ORG = "measure-calculation";

    private static final String ENTITIES = """
            entityDefinitions:
              - code: parcel-item
                name: Parcel Item
                status: ACTIVE
                isEmbedded: true
                componentParents: [ parcel ]
                attributes:
                  - { code: label, name: Label, displayOrder: 1, valueKind: TEXT, formControlType: TEXT_BOX, required: true }
                  - { code: weight, name: Weight, displayOrder: 2, valueKind: REFERENCE, formControlType: QUANTITY, quantityKind: MASS }
                  - { code: price, name: Price, displayOrder: 3, valueKind: REFERENCE, formControlType: MONEY, allowedCurrencies: [ EUR ] }
              - code: parcel
                name: Parcel
                status: ACTIVE
                isEmbedded: false
                componentParents: [ ]
                attributes:
                  - { code: reference, name: Reference, displayOrder: 1, valueKind: TEXT, formControlType: TEXT_BOX, required: true }
                  - { code: totalWeight, name: Total Weight, displayOrder: 2, valueKind: REFERENCE, formControlType: QUANTITY, quantityKind: MASS, allowedUnits: [ kg, g ], defaultUnit: kg, scale: 3 }
                  - { code: totalPrice, name: Total Price, displayOrder: 3, valueKind: REFERENCE, formControlType: MONEY, allowedCurrencies: [ EUR ], defaultCurrency: EUR }
                  - code: items
                    name: Items
                    displayOrder: 4
                    valueKind: REFERENCE
                    formControlType: EMBEDDED_COMPONENTS
                    linkedEntityType: parcel-item
                    multiplicity: '0..n'
            """;

    private static final String CALCULATION = """
            calculation: parcel-totals
            name: Parcel totals
            parameters:
              parcel: { entity: parcel }
            inputs:
              items: { from: parcel, path: items, entity: parcel-item }
            decisions:
              weights:
                for: items
                expr: "weight.baseAmount"
              weightTotal:
                requires: [weights]
                aggregate: { sum: weights }
              prices:
                for: items
                expr: "number(price.amount)"
              priceTotal:
                requires: [prices]
                aggregate: { sum: prices }
            output:
              entity: parcel
              mapping: { totalWeight: weightTotal, totalPrice: priceTotal }
              materialization: stored
            trigger:
              onChange: { entity: parcel }
            """;

    @Autowired private ImportEntityDefinitions importEntities;
    @Autowired private EntityDefinitionReleaseParticipant entityRelease;
    @Autowired private ImportCalculationDefinitions importCalculations;
    @Autowired private CalculationReleaseParticipant calculationRelease;
    @Autowired private CreateEntityInstanceUseCase createEntity;
    @Autowired private UpdateEntityInstanceUseCase updateEntity;
    @Autowired private EntityObjectAccess entities;
    @Autowired private FindCalculationRuns runs;

    @BeforeEach
    void seed() throws IOException {
        assertThat(importEntities.execute(ORG, new ByteArrayInputStream(ENTITIES.getBytes(StandardCharsets.UTF_8))).errors()).isEmpty();
        entityRelease.release(ORG);
        assertThat(importCalculations.execute(ORG, CALCULATION).errors()).isEmpty();
        calculationRelease.release(ORG);
    }

    private static Map<String, Object> item(String label, String weightAmount, String weightUnit, String price) {
        return Map.of("label", label, "weight", Map.of("amount", weightAmount, "unit", weightUnit), "price", Map.of("amount", price, "currency", "EUR"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> measure(EntityObjectView parcel, String attribute) {
        return (Map<String, Object>) parcel.payload().get(attribute);
    }

    @Test
    void theTotalsAreStoredAsQuantityAndMoneyInTheAttributesDefaultUnitAndCurrency() {
        UUID id = createEntity.create(ORG, "parcel", Map.of("reference", "P-1",
                "items", List.of(item("A", "250", "g", "19.99"), item("B", "1.5", "kg", "49.90")))).getId();

        EntityObjectView parcel = entities.find(ORG, "parcel", id);

        assertThat(measure(parcel, "totalWeight")).as("250 g + 1.5 kg, summed in kilograms")
                .containsEntry("amount", "1.75").containsEntry("unit", "kg").containsEntry("baseAmount", 1.75);
        assertThat(measure(parcel, "totalPrice")).containsEntry("amount", "69.89").containsEntry("currency", "EUR").containsEntry("baseAmount", 69.89);
        assertThat(runs.findAll(ORG, "parcel-totals", id.toString())).first().extracting(CalculationRun::getStatus).isEqualTo(RunStatus.COMPLETED);
    }

    @Test
    void theTotalsFollowTheRowsAndTheWeightIsRoundedToTheAttributesDecimalPlaces() {
        UUID id = createEntity.create(ORG, "parcel", Map.of("reference", "P-2", "items", List.of(item("A", "1", "kg", "1")))).getId();
        EntityObjectView created = entities.find(ORG, "parcel", id);

        Map<String, Object> edited = new LinkedHashMap<>(created.payload());
        edited.put("items", List.of(item("A", "333.3333", "g", "10.01"), item("B", "333.3333", "g", "10.01"), item("C", "333.3333", "g", "10.01")));
        edited.put("totalWeight", Map.of("amount", "1", "unit", "kg"));
        updateEntity.update(ORG, "parcel", id, created.version(), edited);

        EntityObjectView updated = entities.find(ORG, "parcel", id);
        assertThat(measure(updated, "totalWeight")).as("0.9999999 kg, three decimal places").containsEntry("amount", "1").containsEntry("unit", "kg");
        assertThat(measure(updated, "totalPrice")).containsEntry("amount", "30.03").containsEntry("currency", "EUR");
        assertThat(updated.version()).as("the calculated write keeps the version the form saved").isEqualTo(created.version() + 1);
    }
}
