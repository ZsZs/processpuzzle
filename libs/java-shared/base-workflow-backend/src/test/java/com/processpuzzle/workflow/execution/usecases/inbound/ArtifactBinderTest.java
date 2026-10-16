package com.processpuzzle.workflow.execution.usecases.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.shared.event.EventParameterType;
import com.processpuzzle.shared.event.OccurredParameter;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactType;
import com.processpuzzle.workflow.definition.domain.ArtifactUse;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.execution.domain.ArtifactInstance;
import com.processpuzzle.workflow.execution.domain.ArtifactInstanceRepository;
import com.processpuzzle.workflow.execution.usecases.outbound.EntityStateGateway;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** docs/event-parameters.md: a record reference binds the run's artifact of its type — once. */
class ArtifactBinderTest {

    private static final String ORG = "org-1";
    private static final UUID RUN = UUID.randomUUID();

    private static final ArtifactDefinition ORDER = ArtifactDefinition.builder().id("order-entity").name("Order")
            .artifactType(ArtifactType.ENTITY).artifactTypeId("order").stateMachineId("order").build();
    private static final ArtifactDefinition REPLACEMENT = ArtifactDefinition.builder().id("replacement-order").name("Replacement")
            .artifactType(ArtifactType.ENTITY).artifactTypeId("order").build();
    private static final ArtifactDefinition INVOICE = ArtifactDefinition.builder().id("fulfillment-invoice").name("Invoice")
            .artifactType(ArtifactType.DOCUMENT).artifactTypeId("fulfillment-invoice").build();

    private final ArtifactInstanceRepository repository = mock(ArtifactInstanceRepository.class);
    private final EntityStateGateway states = mock(EntityStateGateway.class);
    private final ArtifactBinder binder = new ArtifactBinder(repository, states);

    private ArtifactInstance order;
    private ArtifactInstance replacement;
    private ArtifactInstance invoice;

    @BeforeEach
    void setUp() {
        order = instance(ORDER);
        replacement = instance(REPLACEMENT);
        invoice = instance(INVOICE);
        when(repository.findByOrgKeyAndWorkflowInstanceId(ORG, RUN)).thenReturn(List.of(order, replacement, invoice));
        when(states.isAvailable()).thenReturn(true);
        when(states.currentState(ORG, "order", "o-1")).thenReturn("DRAFT");
    }

    @Test
    void bindsTheArtifactOfTheReferencesType_andReadsItsState() {
        Map<String, Object> variables = binder.bind(ORG, RUN, workflow(use(ORDER, "new_order", null), use(INVOICE, "invoice", null)),
                "order-created", List.of(reference("subject", "Order", "o-1")));

        assertThat(order.getEntityId()).isEqualTo("o-1");
        assertThat(order.getEntityType()).isEqualTo("Order");
        assertThat(order.getCurrentState()).isEqualTo("DRAFT");
        assertThat(invoice.getEntityId()).isNull();
        assertThat(variables).containsExactly(Map.entry("new_order", "o-1"));
    }

    @Test
    void bindsADocumentArtifactFromADocumentReference() {
        binder.bind(ORG, RUN, workflow(use(ORDER, "new_order", null), use(INVOICE, "invoice", null)), "invoice-issued",
                List.of(reference("invoice", "document", "d-7")));

        assertThat(invoice.getEntityId()).isEqualTo("d-7");
        assertThat(invoice.getCurrentState()).isNull();
    }

    // Decision 2: an artifact bound once keeps its record.
    @Test
    void neverReBinds() {
        order.setEntityId("o-1");

        binder.bind(ORG, RUN, workflow(use(ORDER, "new_order", null)), "order-changed", List.of(reference("subject", "order", "o-2")));

        assertThat(order.getEntityId()).isEqualTo("o-1");
        verify(repository, never()).save(any());
    }

    // Two artifacts of one type: the reference a bindFrom names goes there, the rest to the one naming none.
    @Test
    void followsBindFromWhenTwoArtifactsShareAType() {
        ResolvedWorkflow workflow = workflow(use(ORDER, "new_order", null), use(REPLACEMENT, "replacement", "order-replaced.replacement"));

        binder.bind(ORG, RUN, workflow, "order-replaced", List.of(reference("replacement", "order", "o-9")));
        binder.bind(ORG, RUN, workflow, "order-created", List.of(reference("subject", "order", "o-1")));

        assertThat(replacement.getEntityId()).isEqualTo("o-9");
        assertThat(order.getEntityId()).isEqualTo("o-1");
    }

    @Test
    void bindsNothingWhenTheTypeIsAmbiguous() {
        binder.bind(ORG, RUN, workflow(use(ORDER, "new_order", null), use(REPLACEMENT, "replacement", null)), "order-created",
                List.of(reference("subject", "order", "o-1")));

        assertThat(order.getEntityId()).isNull();
        assertThat(replacement.getEntityId()).isNull();
    }

    @Test
    void ignoresPlainValuesAndEmptyReferences() {
        binder.bind(ORG, RUN, workflow(use(ORDER, "new_order", null)), "order-created", List.of(
                new OccurredParameter("orderNumber", EventParameterType.STRING, null, "O-1"),
                reference("subject", "order", null)));

        assertThat(order.getEntityId()).isNull();
    }

    // The record a person attached: a task contributing the object's name binds it.
    @Test
    void bindsAnArtifactATaskNamesByItsObjectName() {
        Map<String, Object> variables = binder.bindByObjectNames(ORG, RUN, workflow(use(ORDER, "new_order", null), use(INVOICE, "invoice", null)),
                Map.of("invoice", "d-7", "note", "checked"));

        assertThat(invoice.getEntityId()).isEqualTo("d-7");
        assertThat(invoice.getEntityType()).isEqualTo(ArtifactBinder.DOCUMENT_TYPE);
        assertThat(order.getEntityId()).isNull();
        assertThat(variables).containsExactly(Map.entry("invoice", "d-7"));
    }

    // base-state that cannot tell leaves the state unknown rather than failing the binding.
    @Test
    void bindsEvenWhenTheStateCannotBeRead() {
        when(states.currentState(ORG, "order", "o-1")).thenThrow(new IllegalStateException("no machine instance"));

        binder.bind(ORG, RUN, workflow(use(ORDER, "new_order", null)), "order-created", List.of(reference("subject", "order", "o-1")));

        assertThat(order.getEntityId()).isEqualTo("o-1");
        assertThat(order.getCurrentState()).isNull();
    }

    private static ArtifactInstance instance(ArtifactDefinition definition) {
        return ArtifactInstance.builder().id(UUID.randomUUID()).orgKey(ORG).workflowInstanceId(RUN)
                .artifactDefinitionId(definition.getId()).name(definition.getName()).type(definition.getArtifactType()).build();
    }

    private static ArtifactUse use(ArtifactDefinition definition, String objectName, String bindFrom) {
        return ArtifactUse.builder().artifactDefinitionId(definition.getId()).objectName(objectName).bindFrom(bindFrom).build();
    }

    private static ResolvedWorkflow workflow(ArtifactUse... uses) {
        return new ResolvedWorkflow(Workflow.builder().id("fulfillment").artifacts(List.of(uses)).build(), List.of(),
                List.of(ORDER, REPLACEMENT, INVOICE), List.of());
    }

    private static OccurredParameter reference(String name, String entityType, String id) {
        return new OccurredParameter(name, EventParameterType.ENTITY_REF, entityType, id);
    }
}
