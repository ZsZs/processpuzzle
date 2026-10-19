package com.processpuzzle.workflow.definition.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.processpuzzle.workflow.definition.domain.StateTransitionCoverage.Gap;
import com.processpuzzle.workflow.definition.usecases.outbound.CatalogStateChange;
import com.processpuzzle.workflow.definition.usecases.outbound.StateMachineCatalogPort.Machine;
import com.processpuzzle.workflow.definition.usecases.outbound.StateMachineCatalogPort.Transition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The seeded order: its machine cancels it from DRAFT and from CONFIRMED, and order-fulfillment-workflow's first
 * two tasks wait on it in exactly those states.
 */
class StateTransitionCoverageTest {

    private static final Machine ORDER_MACHINE = new Machine("order", List.of("DRAFT", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED"),
            List.of(new Transition("confirm", "Confirm", "DRAFT", "CONFIRMED"),
                    new Transition("ship", "Ship", "CONFIRMED", "SHIPPED"),
                    new Transition("deliver", "Deliver", "SHIPPED", "DELIVERED"),
                    new Transition("cancel-draft", "Cancel (from Draft)", "DRAFT", "CANCELLED"),
                    new Transition("cancel-confirmed", "Cancel (from Confirmed)", "CONFIRMED", "CANCELLED")));

    private static final ArtifactDefinition ORDER = ArtifactDefinition.builder().id("order-entity").name("Order")
            .artifactType(ArtifactType.ENTITY).artifactTypeId("order").stateMachineId("order").build();

    private static final Map<String, CatalogStateChange> CATALOG = Map.of(
            "OrderCancelledEvent", new CatalogStateChange("order", "CANCELLED"),
            "OrderChangedEvent", new CatalogStateChange("order", null),
            "InvoiceCancelledEvent", new CatalogStateChange("invoice", "CANCELLED"));

    @Test
    void reportsEveryTransitionOutOfAWaitedStateTheWorkflowDoesNotHandle() {
        List<Gap> gaps = gapsOf(fulfillment());

        assertThat(gaps).extracting(Gap::taskDefinitionId, Gap::fromState, gap -> gap.transition().key())
                .containsExactly(tuple("review-order", "DRAFT", "cancel-draft"), tuple("approve-shipment", "CONFIRMED", "cancel-confirmed"));
    }

    // The task's own output transition is the one it makes, so it is handled by definition.
    @Test
    void acceptsTheTransitionTheTaskMakes() {
        assertThat(gapsOf(fulfillment())).noneMatch(gap -> gap.transition().key().equals("confirm") || gap.transition().key().equals("ship"));
    }

    @Test
    void acceptsATransitionCaughtOnTheTask() {
        Workflow workflow = fulfillment();
        workflow.getEvents().add(cancelCatch("order-cancelled-in-review", "review-order", "OrderCancelledEvent"));
        workflow.getEvents().add(cancelCatch("order-cancelled-before-shipment", "approve-shipment", "OrderCancelledEvent"));

        assertThat(gapsOf(workflow)).isEmpty();
    }

    // A state change with no state named is any change of the subject, so it catches every transition.
    @Test
    void acceptsACatchOfAnyStateChangeOfTheSubject() {
        Workflow workflow = fulfillment();
        workflow.getEvents().add(cancelCatch("changed-in-review", "review-order", "OrderChangedEvent"));

        assertThat(gapsOf(workflow)).extracting(Gap::taskDefinitionId).containsExactly("approve-shipment");
    }

    // Caught on the other task, or for another subject, the cancellation of this one is still nobody's business.
    @Test
    void doesNotCountACatchOnAnotherTaskOrOfAnotherSubject() {
        Workflow workflow = fulfillment();
        workflow.getEvents().add(cancelCatch("cancelled-elsewhere", "approve-shipment", "OrderCancelledEvent"));
        workflow.getEvents().add(cancelCatch("invoice-cancelled", "review-order", "InvoiceCancelledEvent"));

        assertThat(gapsOf(workflow)).extracting(Gap::taskDefinitionId).containsExactly("review-order");
    }

    @Test
    void doesNotCountAThrow() {
        Workflow workflow = fulfillment();
        EventUse thrown = cancelCatch("announced", "review-order", "OrderCancelledEvent");
        thrown.setDirection(EventDirection.THROW);
        workflow.getEvents().add(thrown);

        assertThat(gapsOf(workflow)).extracting(Gap::taskDefinitionId).contains("review-order");
    }

    // What the check cannot read, it leaves to other checks rather than failing.
    @Test
    void skipsArtifactsWithoutAKnownMachineAndStatesTheMachineDoesNotHave() {
        Workflow workflow = fulfillment();
        workflow.getTasks().getFirst().getArtifactStates().getFirst().setInputState("ARCHIVED");

        assertThat(StateTransitionCoverage.gapsOf(fulfillment(), id -> Optional.of(ORDER), id -> Optional.empty(), id -> Optional.ofNullable(CATALOG.get(id))))
                .isEmpty();
        assertThat(gapsOf(workflow)).extracting(Gap::taskDefinitionId).containsExactly("approve-shipment");
    }

    private static List<Gap> gapsOf(Workflow workflow) {
        return StateTransitionCoverage.gapsOf(workflow,
                id -> id.equals(ORDER.getId()) ? Optional.of(ORDER) : Optional.empty(),
                id -> id.equals("order") ? Optional.of(ORDER_MACHINE) : Optional.empty(),
                id -> Optional.ofNullable(CATALOG.get(id)));
    }

    private static Workflow fulfillment() {
        return Workflow.builder().id("order-fulfillment-workflow")
                .tasks(new ArrayList<>(List.of(task("review-order", "DRAFT", "CONFIRMED"), task("approve-shipment", "CONFIRMED", "SHIPPED"),
                        task("confirm-delivery", "SHIPPED", "DELIVERED"))))
                .events(new ArrayList<>())
                .build();
    }

    private static TaskUse task(String id, String inputState, String outputState) {
        return TaskUse.builder().taskDefinitionId(id)
                .artifactStates(new ArrayList<>(List.of(TaskArtifactState.builder().artifactDefinitionId("order-entity")
                        .inputState(inputState).outputState(outputState).build())))
                .build();
    }

    private static EventUse cancelCatch(String id, String attachedTo, String eventDefinitionId) {
        return EventUse.builder().id(id).eventDefinitionId(eventDefinitionId).direction(EventDirection.CATCH).attachedTo(attachedTo)
                .interrupting(true).build();
    }
}
