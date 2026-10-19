package com.processpuzzle.workflow.definition.domain;

import com.processpuzzle.workflow.definition.usecases.outbound.CatalogStateChange;
import com.processpuzzle.workflow.definition.usecases.outbound.StateMachineCatalogPort.Machine;
import com.processpuzzle.workflow.definition.usecases.outbound.StateMachineCatalogPort.Transition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Whether a workflow handles everything its artifacts' state machines allow while its tasks wait on them.
 *
 * <p>A task that expects an artifact in a state — {@link TaskArtifactState#getInputState()} — is working on the
 * object while it is there. The object's state machine says where it can go from there, and every one of those
 * transitions has to be the workflow's business, or the run is left waiting on a task about an object that has
 * already moved on: an order cancelled in review leaves "Review Order" open forever.
 *
 * <p>A transition is <em>handled</em> when
 * <ul>
 *   <li>it is the one the task makes — its target is the task's {@link TaskArtifactState#getOutputState()} for that
 *   artifact; or</li>
 *   <li>a catch event attached to the task waits for it — a SYSTEM event of the catalog standing for the object
 *   reaching the transition's target ({@link CatalogStateChange}). Interrupting or not: either way the workflow has
 *   said what happens then.</li>
 * </ul>
 *
 * <p>Everything the check cannot read it skips rather than fails: an artifact with no state machine, a machine
 * base-state does not know, a task expecting a state the machine does not have. Those are other problems, and the
 * release's reference check or the designer's eye is where they belong.
 */
public final class StateTransitionCoverage {

    /** One transition no part of the workflow handles. */
    public record Gap(String taskDefinitionId, String artifactDefinitionId, String stateMachineId, String fromState,
                      Transition transition) {}

    private StateTransitionCoverage() {}

    /**
     * The transitions {@code workflow} leaves unhandled, in task order.
     *
     * @param artifacts    the artifact definition a workflow names, by id
     * @param machines     the state machine an artifact's {@code stateMachineId} names, by that id
     * @param stateChanges the state change a catalog event stands for, by event definition id
     */
    public static List<Gap> gapsOf(Workflow workflow, Function<String, Optional<ArtifactDefinition>> artifacts,
                                   Function<String, Optional<Machine>> machines,
                                   Function<String, Optional<CatalogStateChange>> stateChanges) {
        List<Gap> gaps = new ArrayList<>();
        for (TaskUse task : nonNull(workflow.getTasks())) {
            List<CatalogStateChange> caught = caughtOn(workflow, task.getTaskDefinitionId(), stateChanges);
            nonNull(task.getArtifactStates()).stream()
                    .filter(state -> !isBlank(state.getInputState()))
                    .forEach(state -> gaps.addAll(gapsOf(task, state, caught, artifacts, machines)));
        }
        return gaps;
    }

    /** The transitions out of the state one task expects one artifact in that neither the task nor a catch on it handles. */
    private static List<Gap> gapsOf(TaskUse task, TaskArtifactState state, List<CatalogStateChange> caught,
                                    Function<String, Optional<ArtifactDefinition>> artifacts,
                                    Function<String, Optional<Machine>> machines) {
        Optional<String> machineId = artifacts.apply(state.getArtifactDefinitionId())
                .map(ArtifactDefinition::getStateMachineId)
                .filter(id -> !isBlank(id));
        Optional<Machine> machine = machineId.flatMap(machines).filter(candidate -> candidate.hasState(state.getInputState()));
        if (machine.isEmpty()) {
            return List.of();
        }
        return machine.get().transitionsFrom(state.getInputState()).stream()
                .filter(transition -> !isHandled(transition, state, machine.get(), caught))
                .map(transition -> new Gap(task.getTaskDefinitionId(), state.getArtifactDefinitionId(), machineId.get(),
                        state.getInputState(), transition))
                .toList();
    }

    private static boolean isHandled(Transition transition, TaskArtifactState state, Machine machine,
                                     List<CatalogStateChange> caught) {
        String target = transition.targetStateKey();
        if (target != null && target.equalsIgnoreCase(state.getOutputState())) {
            return true;
        }
        return caught.stream().anyMatch(change -> change.reaches(machine.entityName(), target));
    }

    /** The state changes the catch events attached to the task wait for. */
    private static List<CatalogStateChange> caughtOn(Workflow workflow, String taskDefinitionId,
                                                     Function<String, Optional<CatalogStateChange>> stateChanges) {
        return nonNull(workflow.getEvents()).stream()
                .filter(EventUse::isCatch)
                .filter(event -> Objects.equals(event.getAttachedTo(), taskDefinitionId))
                .filter(event -> !isBlank(event.getEventDefinitionId()))
                .map(event -> stateChanges.apply(event.getEventDefinitionId()))
                .flatMap(Optional::stream)
                .toList();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }
}
