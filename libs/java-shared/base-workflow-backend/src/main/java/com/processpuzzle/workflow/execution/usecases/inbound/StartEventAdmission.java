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
import com.processpuzzle.workflow.definition.domain.WorkflowStartConditionType;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.execution.usecases.outbound.EntityStateGateway;
import com.processpuzzle.workflow.execution.usecases.outbound.PermitAllStartAuthorizationPort;
import com.processpuzzle.workflow.execution.usecases.outbound.StartAuthorizationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Decides whether an explicit start through {@code /instances} is admitted by one of the workflow's
 * start events, and by which. A workflow without start events admits anyone; otherwise the first
 * event that admits wins:
 *
 * <ul>
 *   <li>ROLE_DEFINITION — the caller holds one of {@code authorizedRoles}, asked through
 *       {@link StartAuthorizationPort}. No roles named means anyone; a role with no
 *       {@code entityRoleId} admits, as it does in {@code AssignTaskUseCase}.
 *   <li>INPUT_ARTIFACT — an {@code entityId} is given and every required ENTITY artifact with a
 *       required state is in it. An unavailable state gateway, like every port fallback here, permits.
 *   <li>TRIGGERING_EVENT, TIME_BASED_PRECONDITION — never: they start the workflow on their own.
 *       A TRIGGERING_EVENT start does so through {@code TriggeredStartListener} and
 *       {@code StartWorkflowInstanceUseCase.startTriggered}, which does not come here at all — the
 *       event has matched already, and there is no caller to admit. TIME_BASED_PRECONDITION has no
 *       trigger yet.
 * </ul>
 *
 * <p>When nothing admits, a workflow whose events are all of the last kind answers 409 — the start
 * is not refused for this caller, it is not a thing this workflow does — and any other answers 403.
 */
@Component
public class StartEventAdmission {

    private final RoleDefinitionRepository roleRepository;
    private final EntityStateGateway entityStateGateway;
    private final StartAuthorizationPort startAuthorizationPort;

    public StartEventAdmission(RoleDefinitionRepository roleRepository, EntityStateGateway entityStateGateway,
                               ObjectProvider<StartAuthorizationPort> startAuthorizationPortProvider) {
        this.roleRepository = roleRepository;
        this.entityStateGateway = entityStateGateway;
        this.startAuthorizationPort = startAuthorizationPortProvider.getIfUnique(PermitAllStartAuthorizationPort::new);
    }

    /**
     * @param startEventId the event to admit the start by; null tries every event in declaration order
     * @return the id of the admitting start event; empty when the workflow has none
     */
    public Optional<String> admit(String orgKey, ResolvedWorkflow workflow, String startEventId, String entityId) {
        List<StartEvent> startEvents = workflow.definition().getStartEvents() == null
                ? List.of()
                : workflow.definition().getStartEvents();
        if (startEvents.isEmpty()) {
            return Optional.empty();
        }

        List<StartEvent> candidates = startEventId == null ? startEvents : List.of(find(startEvents, startEventId, workflow));
        if (candidates.stream().noneMatch(StartEvent::admitsManualStart)) {
            throw new ConflictException("workflow.startNotManual",
                    "Workflow '%s' starts on its own — its start events %s do not admit a start by hand"
                            .formatted(workflow.id(), ids(candidates)));
        }

        for (StartEvent candidate : candidates) {
            if (admits(orgKey, workflow, candidate, entityId)) {
                return Optional.of(candidate.getId());
            }
        }
        throw new ForbiddenException("workflow.startRefused",
                "No start event of workflow '%s' admits this start; refused by %s"
                        .formatted(workflow.id(), ids(candidates.stream().filter(StartEvent::admitsManualStart).toList())));
    }

    private StartEvent find(List<StartEvent> startEvents, String startEventId, ResolvedWorkflow workflow) {
        return startEvents.stream()
                .filter(event -> startEventId.equals(event.getId()))
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        "Workflow '%s' has no start event '%s'".formatted(workflow.id(), startEventId)));
    }

    private boolean admits(String orgKey, ResolvedWorkflow workflow, StartEvent startEvent, String entityId) {
        if (startEvent.getStartType() == WorkflowStartConditionType.ROLE_DEFINITION) {
            return callerHoldsAuthorizedRole(orgKey, startEvent);
        }
        if (startEvent.getStartType() == WorkflowStartConditionType.INPUT_ARTIFACT) {
            return entityId != null && requiredArtifactsInState(orgKey, workflow, startEvent, entityId);
        }
        return false;
    }

    private boolean callerHoldsAuthorizedRole(String orgKey, StartEvent startEvent) {
        List<String> authorizedRoles = startEvent.getAuthorizedRoles() == null ? List.of() : startEvent.getAuthorizedRoles();
        if (authorizedRoles.isEmpty()) {
            return true;
        }
        Set<String> entityRoleIds = new HashSet<>();
        for (String roleId : authorizedRoles) {
            String entityRoleId = roleRepository.findByOrgKeyAndId(orgKey, roleId)
                    .map(RoleDefinition::getEntityRoleId)
                    .orElse(null);
            if (entityRoleId == null || entityRoleId.isBlank()) {
                // Nothing to check membership against.
                return true;
            }
            entityRoleIds.add(entityRoleId);
        }
        return startAuthorizationPort.currentPrincipalHoldsAny(orgKey, entityRoleIds);
    }

    private boolean requiredArtifactsInState(String orgKey, ResolvedWorkflow workflow, StartEvent startEvent, String entityId) {
        List<RequiredStartArtifact> required =
                startEvent.getRequiredArtifacts() == null ? List.of() : startEvent.getRequiredArtifacts();
        for (RequiredStartArtifact artifact : required) {
            if (artifact.getState() == null || !entityStateGateway.isAvailable()) {
                continue;
            }
            Optional<String> entityName = entityNameOf(workflow, artifact.getArtifactDefinitionId());
            if (entityName.isPresent()
                    && !artifact.getState().equals(entityStateGateway.currentState(orgKey, entityName.get(), entityId))) {
                return false;
            }
        }
        return true;
    }

    /** The base-entity name of an ENTITY artifact; empty for any other type — there is no state to read. */
    private Optional<String> entityNameOf(ResolvedWorkflow workflow, String artifactDefinitionId) {
        return workflow.artifacts().stream()
                .filter(definition -> definition.getId().equals(artifactDefinitionId))
                .filter(definition -> definition.getArtifactType() == ArtifactType.ENTITY)
                .map(ArtifactDefinition::getArtifactTypeId)
                .filter(name -> name != null && !name.isBlank())
                .findFirst();
    }

    private static List<String> ids(List<StartEvent> startEvents) {
        return startEvents.stream().map(StartEvent::getId).toList();
    }
}
