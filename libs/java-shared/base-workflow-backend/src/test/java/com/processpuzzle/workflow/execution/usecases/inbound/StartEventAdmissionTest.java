package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.ForbiddenException;
import com.processpuzzle.workflow.common.ValidationException;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactType;
import com.processpuzzle.workflow.definition.domain.RequiredStartArtifact;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.RoleDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.StartEvent;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowStartConditionType;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.execution.usecases.outbound.EntityStateGateway;
import com.processpuzzle.workflow.execution.usecases.outbound.StartAuthorizationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StartEventAdmissionTest {

    private static final String ORG = "acme";

    private RoleDefinitionRepository roleRepository;
    private EntityStateGateway entityStateGateway;
    private StartAuthorizationPort startAuthorizationPort;
    private StartEventAdmission admission;

    private final ArtifactDefinition order = ArtifactDefinition.builder().orgKey(ORG).id("order-entity").name("Order")
            .artifactType(ArtifactType.ENTITY).artifactTypeId("Order").build();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        roleRepository = mock(RoleDefinitionRepository.class);
        entityStateGateway = mock(EntityStateGateway.class);
        startAuthorizationPort = mock(StartAuthorizationPort.class);
        when(entityStateGateway.isAvailable()).thenReturn(true);

        ObjectProvider<StartAuthorizationPort> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique(any(Supplier.class))).thenReturn(startAuthorizationPort);
        admission = new StartEventAdmission(roleRepository, entityStateGateway, provider);
    }

    @Test
    void aWorkflowWithoutStartEventsAdmitsAnyone() {
        assertThat(admission.admit(ORG, workflow(), null, null)).isEmpty();
    }

    @Test
    void roleDefinitionWithoutRolesAdmitsAnyone() {
        assertThat(admission.admit(ORG, workflow(roleEvent("manual")), null, null)).contains("manual");
        verify(startAuthorizationPort, never()).currentPrincipalHoldsAny(anyString(), any());
    }

    @Test
    void roleDefinitionAdmitsAHolderOfAnAuthorizedRole() {
        when(roleRepository.findByOrgKeyAndId(ORG, "clerk")).thenReturn(Optional.of(role("clerk", "order-clerk")));
        when(startAuthorizationPort.currentPrincipalHoldsAny(ORG, Set.of("order-clerk"))).thenReturn(true);

        assertThat(admission.admit(ORG, workflow(roleEvent("manual", "clerk")), null, null)).contains("manual");
    }

    @Test
    void roleDefinitionRefusesACallerWithoutTheRole() {
        when(roleRepository.findByOrgKeyAndId(ORG, "clerk")).thenReturn(Optional.of(role("clerk", "order-clerk")));
        when(startAuthorizationPort.currentPrincipalHoldsAny(ORG, Set.of("order-clerk"))).thenReturn(false);

        assertThatThrownBy(() -> admission.admit(ORG, workflow(roleEvent("manual", "clerk")), null, null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("refused by [manual]")
                .extracting(ex -> ((ForbiddenException) ex).getErrorId()).isEqualTo("workflow.startRefused");
    }

    /** A role with no entity role behind it gives membership nothing to check, as in AssignTaskUseCase. */
    @Test
    void roleDefinitionAdmitsWhenARoleHasNoEntityRole() {
        when(roleRepository.findByOrgKeyAndId(ORG, "clerk")).thenReturn(Optional.of(role("clerk", null)));

        assertThat(admission.admit(ORG, workflow(roleEvent("manual", "clerk")), null, null)).contains("manual");
        verify(startAuthorizationPort, never()).currentPrincipalHoldsAny(anyString(), any());
    }

    @Test
    void inputArtifactAdmitsAnEntityInTheRequiredState() {
        when(entityStateGateway.currentState(ORG, "Order", "o-1")).thenReturn("DRAFT");

        assertThat(admission.admit(ORG, workflow(artifactEvent("drafted", "DRAFT")), null, "o-1")).contains("drafted");
    }

    @Test
    void inputArtifactRefusesAnEntityInAnotherState() {
        when(entityStateGateway.currentState(ORG, "Order", "o-1")).thenReturn("SHIPPED");

        assertThatThrownBy(() -> admission.admit(ORG, workflow(artifactEvent("drafted", "DRAFT")), null, "o-1"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void inputArtifactRefusesAStartWithoutAnEntity() {
        assertThatThrownBy(() -> admission.admit(ORG, workflow(artifactEvent("drafted", "DRAFT")), null, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void inputArtifactWithoutARequiredStateAdmitsAnyEntity() {
        assertThat(admission.admit(ORG, workflow(artifactEvent("any", null)), null, "o-1")).contains("any");
        verify(entityStateGateway, never()).currentState(anyString(), anyString(), anyString());
    }

    /** A guard only: with no state module there is nothing to compare against, so it permits. */
    @Test
    void inputArtifactPermitsWhenTheStateGatewayIsUnavailable() {
        when(entityStateGateway.isAvailable()).thenReturn(false);

        assertThat(admission.admit(ORG, workflow(artifactEvent("drafted", "DRAFT")), null, "o-1")).contains("drafted");
        verify(entityStateGateway, never()).currentState(anyString(), anyString(), anyString());
    }

    @Test
    void aWorkflowThatOnlyStartsOnItsOwnAnswersConflict() {
        StartEvent triggered = StartEvent.builder().id("submitted")
                .startType(WorkflowStartConditionType.TRIGGERING_EVENT).eventType("order.submitted").build();
        StartEvent scheduled = StartEvent.builder().id("milestone")
                .startType(WorkflowStartConditionType.TIME_BASED_PRECONDITION).build();

        assertThatThrownBy(() -> admission.admit(ORG, workflow(triggered, scheduled), null, null))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorId()).isEqualTo("workflow.startNotManual");
    }

    /** A manual event next to a triggered one keeps the workflow startable by hand. */
    @Test
    void anyAdmittingEventWinsInDeclarationOrder() {
        StartEvent triggered = StartEvent.builder().id("submitted")
                .startType(WorkflowStartConditionType.TRIGGERING_EVENT).build();
        when(entityStateGateway.currentState(ORG, "Order", "o-1")).thenReturn("SHIPPED");

        assertThat(admission.admit(ORG, workflow(triggered, artifactEvent("drafted", "DRAFT"), roleEvent("manual")),
                null, "o-1")).contains("manual");
    }

    @Test
    void anExplicitStartEventIsTheOnlyOneChecked() {
        when(entityStateGateway.currentState(ORG, "Order", "o-1")).thenReturn("SHIPPED");
        ResolvedWorkflow workflow = workflow(artifactEvent("drafted", "DRAFT"), roleEvent("manual"));

        assertThatThrownBy(() -> admission.admit(ORG, workflow, "drafted", "o-1"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("refused by [drafted]");
        assertThat(admission.admit(ORG, workflow, "manual", "o-1")).contains("manual");
    }

    @Test
    void anExplicitNonManualStartEventAnswersConflict() {
        StartEvent triggered = StartEvent.builder().id("submitted")
                .startType(WorkflowStartConditionType.TRIGGERING_EVENT).build();

        assertThatThrownBy(() -> admission.admit(ORG, workflow(triggered, roleEvent("manual")), "submitted", null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void anUnknownStartEventIdIsAValidationError() {
        assertThatThrownBy(() -> admission.admit(ORG, workflow(roleEvent("manual")), "ghost", null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no start event 'ghost'");
    }

    // ---------------------------------------------------------------- fixtures

    private ResolvedWorkflow workflow(StartEvent... startEvents) {
        Workflow definition = Workflow.builder().orgKey(ORG).id("order-fulfillment").name("Order Fulfillment")
                .startEvents(List.of(startEvents)).build();
        return new ResolvedWorkflow(definition, List.of(), List.of(order), List.of());
    }

    private StartEvent roleEvent(String id, String... authorizedRoles) {
        return StartEvent.builder().id(id).startType(WorkflowStartConditionType.ROLE_DEFINITION)
                .authorizedRoles(List.of(authorizedRoles)).build();
    }

    private StartEvent artifactEvent(String id, String state) {
        return StartEvent.builder().id(id).startType(WorkflowStartConditionType.INPUT_ARTIFACT)
                .requiredArtifacts(List.of(RequiredStartArtifact.builder()
                        .artifactDefinitionId("order-entity").state(state).build()))
                .build();
    }

    private RoleDefinition role(String id, String entityRoleId) {
        return RoleDefinition.builder().orgKey(ORG).id(id).name(id).entityRoleId(entityRoleId).build();
    }
}
