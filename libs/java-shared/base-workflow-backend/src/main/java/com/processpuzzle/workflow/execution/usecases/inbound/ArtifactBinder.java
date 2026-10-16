package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.shared.event.OccurredParameter;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactType;
import com.processpuzzle.workflow.definition.domain.ArtifactUse;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.execution.domain.ArtifactInstance;
import com.processpuzzle.workflow.execution.domain.ArtifactInstanceRepository;
import com.processpuzzle.workflow.execution.usecases.outbound.EntityStateGateway;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Binds a run's artifacts to the records they are in this run (docs/event-parameters.md).
 *
 * <p>Two ways in:
 * <ul>
 *   <li>{@link #bind(String, UUID, ResolvedWorkflow, String, List)} — the ENTITY_REF parameters of the event the run
 *   starts on or a catch receives. A parameter binds the run's artifact of its entity type: the one whose
 *   {@link ArtifactUse#getBindFrom()} names it, or — when no artifact of the type names a source — the only one of
 *   the type. Several of a type without {@code bindFrom} is ambiguous, which the workflow's save refuses; here it binds
 *   none rather than guess.</li>
 *   <li>{@link #bindByObjectNames} — a task completion's context naming an artifact's {@code objectName}: the path for
 *   a record a person attached rather than an event carried.</li>
 * </ul>
 *
 * <p><strong>Never re-binds.</strong> An artifact bound once keeps its record; a later reference to another record of
 * its type is ignored. A newly bound artifact reads its record's current state once, so the dashboard's required-state
 * gate is right from the start; {@code ArtifactStateListener} keeps it current afterwards.
 *
 * <p>Each answers the context variables the binding adds: the record's id under the artifact's {@code objectName}.
 */
@Component
public class ArtifactBinder {

    private static final Logger LOG = LoggerFactory.getLogger(ArtifactBinder.class);

    /** The entity type a DOCUMENT artifact's records have. */
    public static final String DOCUMENT_TYPE = "document";

    private final ArtifactInstanceRepository artifactInstances;
    private final EntityStateGateway entityStates;

    public ArtifactBinder(ArtifactInstanceRepository artifactInstances, EntityStateGateway entityStates) {
        this.artifactInstances = artifactInstances;
        this.entityStates = entityStates;
    }

    /** Binds from the record references among {@code parameters}, carried by the event {@code source} names. */
    public Map<String, Object> bind(String orgKey, UUID workflowInstanceId, ResolvedWorkflow definition, String source,
                                    List<OccurredParameter> parameters) {
        Map<String, Object> variables = new LinkedHashMap<>();
        List<OccurredParameter> references = parameters == null ? List.of() : parameters.stream().filter(OccurredParameter::isReference).toList();
        if (references.isEmpty()) {
            return variables;
        }
        List<ArtifactInstance> instances = artifactInstances.findByOrgKeyAndWorkflowInstanceId(orgKey, workflowInstanceId);
        for (OccurredParameter reference : references) {
            artifactFor(definition, source, reference)
                    .flatMap(use -> instances.stream().filter(instance -> instance.getArtifactDefinitionId().equals(use.getArtifactDefinitionId())).findFirst()
                            .map(instance -> Map.entry(use, instance)))
                    .filter(binding -> binding.getValue().getEntityId() == null)
                    .ifPresent(binding -> bindTo(orgKey, definition, binding.getKey(), binding.getValue(),
                            reference.entityType(), String.valueOf(reference.value()), variables));
        }
        return variables;
    }

    /**
     * Binds every unbound artifact whose {@code objectName} {@code contribution} names — a task completion contributing
     * {@code invoice: <document id>} binds the workflow's {@code invoice} object to that document.
     */
    public Map<String, Object> bindByObjectNames(String orgKey, UUID workflowInstanceId, ResolvedWorkflow definition,
                                                 Map<String, Object> contribution) {
        Map<String, Object> variables = new LinkedHashMap<>();
        if (contribution == null || contribution.isEmpty()) {
            return variables;
        }
        List<ArtifactInstance> instances = artifactInstances.findByOrgKeyAndWorkflowInstanceId(orgKey, workflowInstanceId);
        for (ArtifactUse use : uses(definition)) {
            Object value = isBlank(use.getObjectName()) ? null : contribution.get(use.getObjectName());
            if (value == null || String.valueOf(value).isBlank()) {
                continue;
            }
            instances.stream()
                    .filter(instance -> instance.getArtifactDefinitionId().equals(use.getArtifactDefinitionId()))
                    .filter(instance -> instance.getEntityId() == null)
                    .findFirst()
                    .ifPresent(instance -> bindTo(orgKey, definition, use, instance, entityTypeOf(definition, use).orElse(null),
                            String.valueOf(value), variables));
        }
        return variables;
    }

    /** The artifact {@code reference}, carried by the event {@code source}, binds — see the class comment. */
    private Optional<ArtifactUse> artifactFor(ResolvedWorkflow definition, String source, OccurredParameter reference) {
        List<ArtifactUse> ofType = uses(definition).stream()
                .filter(use -> entityTypeOf(definition, use).filter(type -> sameType(type, reference.entityType())).isPresent())
                .toList();
        Optional<ArtifactUse> named = ofType.stream()
                .filter(use -> Objects.equals(use.bindFromSource(), source) && Objects.equals(use.bindFromParameter(), reference.name()))
                .findFirst();
        if (named.isPresent()) {
            return named;
        }
        List<ArtifactUse> unnamed = ofType.stream().filter(use -> isBlank(use.getBindFrom())).toList();
        return unnamed.size() == 1 ? Optional.of(unnamed.getFirst()) : Optional.empty();
    }

    private void bindTo(String orgKey, ResolvedWorkflow definition, ArtifactUse use, ArtifactInstance instance, String entityType,
                        String entityId, Map<String, Object> variables) {
        instance.setEntityId(entityId);
        instance.setEntityType(entityType);
        instance.setCurrentState(currentStateOf(orgKey, artifactDefinition(definition, use), entityId));
        instance.setUpdatedAt(Instant.now());
        artifactInstances.save(instance);
        if (!isBlank(use.getObjectName())) {
            variables.put(use.getObjectName(), entityId);
        }
    }

    /** The record's state when bound, if its artifact follows a state machine and base-state can tell. */
    private String currentStateOf(String orgKey, Optional<ArtifactDefinition> artifact, String entityId) {
        String machine = artifact.map(ArtifactDefinition::getStateMachineId).filter(id -> !isBlank(id)).orElse(null);
        if (machine == null || !entityStates.isAvailable()) {
            return null;
        }
        try {
            return entityStates.currentState(orgKey, machine, entityId);
        } catch (RuntimeException e) {
            LOG.debug("{}: no current state for {}/{}: {}", orgKey, machine, entityId, e.getMessage());
            return null;
        }
    }

    /** The type of the records an artifact stands for: its entity's definition code, or {@link #DOCUMENT_TYPE}. */
    private static Optional<String> entityTypeOf(ResolvedWorkflow definition, ArtifactUse use) {
        return artifactDefinition(definition, use).flatMap(artifact -> artifact.getArtifactType() == ArtifactType.DOCUMENT
                ? Optional.of(DOCUMENT_TYPE)
                : artifact.getArtifactType() == ArtifactType.ENTITY ? Optional.ofNullable(artifact.getArtifactTypeId()) : Optional.empty());
    }

    private static Optional<ArtifactDefinition> artifactDefinition(ResolvedWorkflow definition, ArtifactUse use) {
        return definition.artifacts().stream().filter(artifact -> artifact.getId().equals(use.getArtifactDefinitionId())).findFirst();
    }

    private static List<ArtifactUse> uses(ResolvedWorkflow definition) {
        List<ArtifactUse> uses = definition.definition().getArtifacts();
        return uses == null ? List.of() : uses;
    }

    /** Entity type names are written by different people: the seed's {@code order} is a descriptor's {@code Order}. */
    static boolean sameType(String left, String right) {
        return left != null && right != null && normalized(left).equals(normalized(right));
    }

    private static String normalized(String name) {
        return name.toLowerCase().replaceAll("[\\s_-]+", "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
