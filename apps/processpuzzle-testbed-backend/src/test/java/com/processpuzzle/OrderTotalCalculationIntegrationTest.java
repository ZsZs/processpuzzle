package com.processpuzzle;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.baseentity.adapter.inbound.EntityDefinitionReleaseParticipant;
import com.processpuzzle.baseentity.api.EntityObjectAccess;
import com.processpuzzle.baseentity.api.EntityObjectView;
import com.processpuzzle.baseentity.definition.usecases.inbound.ImportEntityDefinitions;
import com.processpuzzle.baseentity.instances.usecases.inbound.CreateEntityInstanceUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.UpdateEntityInstanceUseCase;
import com.processpuzzle.calculation.adapter.inbound.CalculationReleaseParticipant;
import com.processpuzzle.calculation.domain.CalculationRun;
import com.processpuzzle.calculation.domain.RunStatus;
import com.processpuzzle.calculation.domain.RunTrigger;
import com.processpuzzle.calculation.usecase.FindCalculationRuns;
import com.processpuzzle.calculation.usecase.ImportCalculationDefinitions;
import com.processpuzzle.state.adapter.inbound.StateMachineReleaseParticipant;
import com.processpuzzle.state.usecase.FireStateTransition;
import com.processpuzzle.state.usecase.ImportStateMachineDefinitions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
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

/**
 * The seeded order total through the real platform: base-entity publishes the order's changes, the calculation
 * writes its total back through the composition adapters, base-entity keeps that total out of a form's reach, and
 * base-state's confirmation freezes it.
 *
 * <p>In an organization of its own, with the testbed's order and order-line definitions and its order state
 * machine, and the seeded calculation file itself.
 */
@SpringBootTest
class OrderTotalCalculationIntegrationTest {

    private static final String ORG = "calculation-order-total";

    private static final String ENTITIES = """
            entityDefinitions:
              - code: order-line
                name: Order Line
                status: ACTIVE
                isEmbedded: true
                componentParents: [ order ]
                attributes:
                  - { code: productName, name: Product, displayOrder: 1, valueKind: TEXT, formControlType: TEXT_BOX, required: true }
                  - { code: quantity, name: Quantity, displayOrder: 2, valueKind: NUMBER, formControlType: TEXT_BOX, required: true }
                  - { code: unitPrice, name: Unit Price, displayOrder: 3, valueKind: NUMBER, formControlType: TEXT_BOX, required: true }
              - code: order
                name: Order
                status: ACTIVE
                isEmbedded: false
                componentParents: [ ]
                attributes:
                  - { code: orderNumber, name: 'Order #', displayOrder: 1, valueKind: TEXT, formControlType: TEXT_BOX, required: true }
                  - code: status
                    name: Status
                    displayOrder: 2
                    valueKind: ENUM
                    formControlType: DROPDOWN
                    enumValues: [ DRAFT, CONFIRMED ]
                  - { code: total, name: Total, displayOrder: 3, valueKind: NUMBER, formControlType: TEXT_BOX }
                  - code: lineItems
                    name: Line Items
                    displayOrder: 4
                    valueKind: REFERENCE
                    formControlType: EMBEDDED_COMPONENTS
                    linkedEntityType: order-line
                    multiplicity: '0..n'
            """;

    private static final String STATE_MACHINES = """
            stateMachines:
              - entityName: order
                name: Order State Machine
                stateAttributeKey: status
                initialStateKey: DRAFT
                states:
                  - { key: DRAFT, name: Draft, isFinal: false, isLocked: false }
                  - { key: CONFIRMED, name: Confirmed, isFinal: false, isLocked: false }
                transitions:
                  - { key: confirm, name: Confirm, sourceStateKey: DRAFT, targetStateKey: CONFIRMED, triggerKey: confirm }
            """;

    @Autowired private ImportEntityDefinitions importEntities;
    @Autowired private EntityDefinitionReleaseParticipant entityRelease;
    @Autowired private ImportStateMachineDefinitions importStateMachines;
    @Autowired private StateMachineReleaseParticipant stateRelease;
    @Autowired private ImportCalculationDefinitions importCalculations;
    @Autowired private CalculationReleaseParticipant calculationRelease;
    @Autowired private CreateEntityInstanceUseCase createEntity;
    @Autowired private UpdateEntityInstanceUseCase updateEntity;
    @Autowired private EntityObjectAccess entities;
    @Autowired private FireStateTransition fireTransition;
    @Autowired private FindCalculationRuns runs;

    @BeforeEach
    void seed() throws IOException {
        assertThat(importEntities.execute(ORG, stream(ENTITIES)).errors()).isEmpty();
        entityRelease.release(ORG);
        assertThat(importStateMachines.execute(ORG, stream(STATE_MACHINES)).errors()).isEmpty();
        stateRelease.release(ORG);
        String seed = new ClassPathResource("default-calculations/base-calculation/processpuzzle-testbed-calculations.yaml")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(importCalculations.execute(ORG, seed).errors()).isEmpty();
        calculationRelease.release(ORG);
    }

    @Test
    void theTotalFollowsTheLinesUntilTheOrderIsConfirmed() {
        UUID orderId = createEntity.create(ORG, "order", Map.of("orderNumber", "ORD-9001",
                "lineItems", List.of(line("Widget A", 2, 19.99), line("Widget B", 1, 49.90)))).getId();

        EntityObjectView created = entities.find(ORG, "order", orderId);
        assertThat(created.payload()).containsEntry("status", "DRAFT");
        assertThat(total(created)).isEqualByComparingTo("89.88");
        assertThat(created.payload().get("lineItems")).asList().first().isInstanceOf(java.util.LinkedHashMap.class);
        assertThat(runs.findAll(ORG, "order-total", orderId.toString())).first()
                .satisfies(run -> {
                    assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
                    assertThat(run.getTriggeredBy()).isEqualTo(RunTrigger.ON_CHANGE);
                });

        // A form sends the whole object back, its stale total included; the calculation's stays and is recomputed.
        Map<String, Object> edited = new LinkedHashMap<>(created.payload());
        edited.put("total", 1);
        edited.put("lineItems", List.of(line("Widget A", 600, 19.99)));
        updateEntity.update(ORG, "order", orderId, created.version(), edited);
        EntityObjectView updated = entities.find(ORG, "order", orderId);
        assertThat(total(updated)).as("11 994 less the 7% tier").isEqualByComparingTo("11154.42");
        assertThat(updated.version()).as("the calculated write keeps the version the form saved").isEqualTo(created.version() + 1);

        fireTransition.execute(ORG, "order", orderId, "confirm", Map.of(), updated.version());
        List<CalculationRun> afterConfirm = runs.findAll(ORG, "order-total", orderId.toString());
        assertThat(afterConfirm.getFirst().getTriggeredBy()).isEqualTo(RunTrigger.FREEZE_STATE);
        assertThat(afterConfirm.getFirst().isFrozen()).isTrue();

        EntityObjectView confirmed = entities.find(ORG, "order", orderId);
        Map<String, Object> late = new LinkedHashMap<>(confirmed.payload());
        late.put("lineItems", List.of(line("Widget A", 1, 1)));
        updateEntity.update(ORG, "order", orderId, confirmed.version(), late);
        assertThat(total(entities.find(ORG, "order", orderId))).as("frozen").isEqualByComparingTo("11154.42");
        assertThat(runs.findAll(ORG, "order-total", orderId.toString())).hasSize(afterConfirm.size());
    }

    private static BigDecimal total(EntityObjectView order) {
        return new BigDecimal(String.valueOf(order.payload().get("total")));
    }

    private static Map<String, Object> line(String product, int quantity, double unitPrice) {
        return Map.of("productName", product, "quantity", quantity, "unitPrice", unitPrice);
    }

    private static ByteArrayInputStream stream(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
