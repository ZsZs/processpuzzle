package com.processpuzzle.workflow.definition.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.shared.event.CatalogEventKind;
import com.processpuzzle.shared.event.EventParameterType;
import com.processpuzzle.workflow.definition.domain.ThrowParameterCoverage.Reason;
import com.processpuzzle.workflow.definition.domain.ThrowParameterCoverage.Shortfall;
import com.processpuzzle.workflow.definition.usecases.outbound.CatalogParameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The seeded invoice round trip: an order created starts the run, a task is done, and the workflow throws
 * InvoiceRequested ({@code order}, {@code orderNumber} required) — then catches InvoiceIssued and throws it on.
 */
class ThrowParameterCoverageTest {

    private static final Map<String, List<CatalogParameter>> PARAMETERS = Map.of(
            "OrderCreatedEvent", List.of(required("orderNumber", EventParameterType.STRING)),
            "InvoiceRequested", List.of(required("order", EventParameterType.ENTITY_REF),
                    required("orderNumber", EventParameterType.STRING), optional("customerName")),
            "InvoiceIssued", List.of(required("orderNumber", EventParameterType.STRING),
                    required("invoiceNumber", EventParameterType.STRING)),
            "OrderReminder", List.of(required("subject", EventParameterType.ENTITY_REF)));

    private static final Map<String, CatalogEventKind> KINDS = Map.of(
            "OrderCreatedEvent", CatalogEventKind.SYSTEM,
            "InvoiceRequested", CatalogEventKind.MESSAGE,
            "InvoiceIssued", CatalogEventKind.MESSAGE);

    @Test
    void aThrowFilledByItsMappingObjectNamesAndTheStartsParametersHasNoShortfall() {
        EventUse request = throwing("request-invoice", "InvoiceRequested", Map.of("order", "$.new_order"), "review");

        assertThat(shortfallsOf(workflow(List.of(request), true))).isEmpty();
    }

    @Test
    void anObjectNameFillsAnEntityParameterByName() {
        EventUse request = throwing("request-invoice", "InvoiceRequested", Map.of(), "review");
        Workflow workflow = workflow(List.of(request), true);
        workflow.getArtifacts().add(ArtifactUse.builder().artifactDefinitionId("order-entity").objectName("order").build());

        assertThat(shortfallsOf(workflow)).isEmpty();
    }

    @Test
    void aRequiredParameterNothingProducesIsUnfilled() {
        EventUse issued = throwing("invoice-issued", "InvoiceIssued", Map.of());

        List<Shortfall> shortfalls = shortfallsOf(workflow(List.of(issued), false));

        assertThat(shortfalls).singleElement().satisfies(shortfall -> {
            assertThat(shortfall.eventUseId()).isEqualTo("invoice-issued");
            assertThat(shortfall.eventDefinitionId()).isEqualTo("InvoiceIssued");
            assertThat(shortfall.parameter()).isEqualTo("invoiceNumber");
            assertThat(shortfall.reason()).isEqualTo(Reason.UNFILLED);
        });
    }

    @Test
    void aNameOnlyAnUpstreamTaskCouldSupplyIsAWarning() {
        EventUse issued = throwing("invoice-issued", "InvoiceIssued", Map.of(), "review");

        List<Shortfall> shortfalls = shortfallsOf(workflow(List.of(issued), true));

        assertThat(shortfalls).singleElement().satisfies(shortfall -> {
            assertThat(shortfall.parameter()).isEqualTo("invoiceNumber");
            assertThat(shortfall.reason()).isEqualTo(Reason.TASK_ONLY);
        });
    }

    @Test
    void aStepOutputMappingProducesItsTarget() {
        EventUse issued = throwing("invoice-issued", "InvoiceIssued", Map.of(), "review");
        TaskDefinition review = TaskDefinition.builder().id("review")
                .steps(List.of(StepDefinition.builder().id("number").outputMapping(Map.of("invoiceNumber", "number")).build()))
                .build();

        assertThat(ThrowParameterCoverage.shortfallsOf(workflow(List.of(issued), true), PARAMETERS::get,
                id -> Optional.ofNullable(KINDS.get(id)), id -> Optional.of(review))).isEmpty();
    }

    @Test
    void twoEventsCarryingTheNameMakeItAmbiguous() {
        EventUse caught = catching("invoice-issued", "InvoiceIssued", "review");
        EventUse forwarded = throwing("forward", "InvoiceIssued", Map.of("invoiceNumber", "$.number"), "invoice-issued");

        List<Shortfall> shortfalls = shortfallsOf(workflow(List.of(caught, forwarded), true));

        assertThat(shortfalls).singleElement().satisfies(shortfall -> {
            assertThat(shortfall.parameter()).isEqualTo("orderNumber");
            assertThat(shortfall.reason()).isEqualTo(Reason.AMBIGUOUS);
            assertThat(shortfall.carriers()).containsExactly("order-created", "invoice-issued");
        });
    }

    @Test
    void aCatchThatIsNotUpstreamProducesNothing() {
        EventUse caught = catching("invoice-issued", "InvoiceIssued");
        caught.setPayloadMapping(Map.of("invoiceNumber", "$.payload.invoiceNumber"));
        EventUse issued = throwing("issued-early", "InvoiceIssued", Map.of());

        List<Shortfall> shortfalls = shortfallsOf(workflow(List.of(caught, issued), false));

        assertThat(shortfalls).extracting(Shortfall::parameter).containsExactly("invoiceNumber");
    }

    @Test
    void aSystemStartEventCarriesItsSubjectWithoutDeclaringIt() {
        EventUse remind = throwing("remind", "OrderReminder", Map.of());

        assertThat(shortfallsOf(workflow(List.of(remind), false))).isEmpty();
    }

    @Test
    void anEventTheCatalogCannotTellIsSkipped() {
        EventUse unknown = throwing("notify", "Unknown", Map.of());

        assertThat(shortfallsOf(workflow(List.of(unknown), false))).isEmpty();
    }

    private static List<Shortfall> shortfallsOf(Workflow workflow) {
        return ThrowParameterCoverage.shortfallsOf(workflow, PARAMETERS::get, id -> Optional.ofNullable(KINDS.get(id)),
                id -> Optional.empty());
    }

    /** Started by OrderCreatedEvent; with {@code withTask}, a "review" task comes first. */
    private static Workflow workflow(List<EventUse> events, boolean withTask) {
        StartEvent start = StartEvent.builder().id("order-created").startType(WorkflowStartConditionType.TRIGGERING_EVENT)
                .eventType("OrderCreatedEvent").build();
        return Workflow.builder().id("order-fulfillment-workflow")
                .startEvents(List.of(start))
                .artifacts(new ArrayList<>(List.of(ArtifactUse.builder().artifactDefinitionId("order-entity").objectName("new_order").build())))
                .tasks(withTask ? List.of(TaskUse.builder().taskDefinitionId("review").build()) : List.of())
                .events(events)
                .build();
    }

    private static EventUse throwing(String id, String definition, Map<String, String> payloadMapping, String... dependsOn) {
        return EventUse.builder().id(id).eventDefinitionId(definition).direction(EventDirection.THROW)
                .payloadMapping(payloadMapping).dependsOn(List.of(dependsOn)).build();
    }

    private static EventUse catching(String id, String definition, String... dependsOn) {
        return EventUse.builder().id(id).eventDefinitionId(definition).direction(EventDirection.CATCH)
                .dependsOn(List.of(dependsOn)).build();
    }

    private static CatalogParameter required(String name, EventParameterType type) {
        return new CatalogParameter(name, type, null, true);
    }

    private static CatalogParameter optional(String name) {
        return new CatalogParameter(name, EventParameterType.STRING, null, false);
    }
}
