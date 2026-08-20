package com.processpuzzle.state.adapter.inbound;

import com.processpuzzle.shared.model.ImportResult;
import com.processpuzzle.state.domain.ActionRef;
import com.processpuzzle.state.domain.GuardRef;
import com.processpuzzle.state.domain.State;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.Transition;
import com.processpuzzle.state.model.AvailableTransition;
import com.processpuzzle.state.model.EntityObjectStateView;
import com.processpuzzle.state.model.PageOfStateMachineDefinition;
import com.processpuzzle.state.model.StateMachineDefinitionInput;
import com.processpuzzle.state.model.TransitionResult;
import com.processpuzzle.state.usecase.AvailableTransitionProjection;
import com.processpuzzle.state.usecase.EntityObjectStateProjection;
import com.processpuzzle.state.usecase.FireStateTransition;
import com.processpuzzle.state.usecase.ImportOutcome;
import com.processpuzzle.state.usecase.TransitionOutcome;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class StateMapper {

    // ── Knowledge layer ────────────────────────────────────────

    /**
     * {@code orgKey} comes from the path, not the body — {@link StateMachineDefinitionInput}
     * deliberately carries no organization key that could contradict the URL, same discipline as
     * {@code RuleMapper.toDomain}.
     */
    public StateMachineDefinition toDomain(String orgKey, StateMachineDefinitionInput input) {
        return StateMachineDefinition.builder()
                .orgKey(orgKey)
                .entityName(input.getEntityName())
                .name(input.getName())
                .description(input.getDescription())
                .stateAttributeKey(input.getStateAttributeKey())
                .initialStateKey(input.getInitialStateKey())
                .states(toDomainStates(input.getStates()))
                .transitions(toDomainTransitions(input.getTransitions()))
                .build();
    }

    public void applyToDomain(StateMachineDefinitionInput input, StateMachineDefinition target) {
        target.replaceTopology(
                input.getName(),
                input.getDescription(),
                input.getStateAttributeKey(),
                input.getInitialStateKey(),
                toDomainStates(input.getStates()),
                toDomainTransitions(input.getTransitions()));
    }

    public com.processpuzzle.state.model.StateMachineDefinition toModel(StateMachineDefinition definition) {
        com.processpuzzle.state.model.StateMachineDefinition model =
                new com.processpuzzle.state.model.StateMachineDefinition(
                        definition.getEntityName(),
                        definition.getName(),
                        definition.getStateAttributeKey(),
                        definition.getInitialStateKey(),
                        toModelStates(definition.getStates()));
        model.setDescription(definition.getDescription());
        model.setTransitions(toModelTransitions(definition.getTransitions()));
        model.setOrgKey(definition.getOrgKey());
        model.setVersion(definition.getVersion());
        model.setCreatedAt(toOffsetDateTime(definition.getCreatedAt()));
        model.setUpdatedAt(toOffsetDateTime(definition.getUpdatedAt()));
        return model;
    }

    public PageOfStateMachineDefinition toModel(Page<StateMachineDefinition> page) {
        List<com.processpuzzle.state.model.StateMachineDefinition> content =
                page.getContent().stream().map(this::toModel).toList();
        return new PageOfStateMachineDefinition()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .number(page.getNumber())
                .size(page.getSize());
    }

    public ImportResult toModel(ImportOutcome outcome) {
        return new ImportResult()
                .created(outcome.created())
                .updated(outcome.updated())
                .errors(outcome.errors());
    }

    private List<State> toDomainStates(List<com.processpuzzle.state.model.State> states) {
        if (states == null) {
            return List.of();
        }
        return states.stream().map(this::toDomainState).toList();
    }

    private State toDomainState(com.processpuzzle.state.model.State s) {
        return new State(
                s.getKey(), s.getName(), s.getDescription(),
                Boolean.TRUE.equals(s.getTerminal()), Boolean.TRUE.equals(s.getLocked()),
                s.getMetadata());
    }

    private List<Transition> toDomainTransitions(List<com.processpuzzle.state.model.Transition> transitions) {
        if (transitions == null) {
            return List.of();
        }
        return transitions.stream().map(this::toDomainTransition).toList();
    }

    private Transition toDomainTransition(com.processpuzzle.state.model.Transition t) {
        List<GuardRef> guards = t.getGuards() == null ? List.of()
                : t.getGuards().stream().map(g -> new GuardRef(g.getBeanName(), g.getParams())).toList();
        List<ActionRef> actions = t.getActions() == null ? List.of()
                : t.getActions().stream().map(a -> new ActionRef(a.getBeanName(), a.getParams())).toList();
        return new Transition(t.getKey(), t.getName(), t.getSourceStateKey(), t.getTargetStateKey(),
                t.getTriggerKey(), guards, actions);
    }

    private List<com.processpuzzle.state.model.State> toModelStates(List<State> states) {
        return states.stream().map(s -> new com.processpuzzle.state.model.State(s.key(), s.name())
                .description(s.description())
                .terminal(s.isFinal())
                .locked(s.isLocked())
                .metadata(s.metadata())).toList();
    }

    private List<com.processpuzzle.state.model.Transition> toModelTransitions(List<Transition> transitions) {
        return transitions.stream().map(t -> new com.processpuzzle.state.model.Transition(
                        t.key(), t.sourceStateKey(), t.targetStateKey(), t.triggerKey())
                .name(t.name())
                .guards(t.guards().stream()
                        .map(g -> new com.processpuzzle.state.model.GuardRef(g.beanName()).params(g.params()))
                        .toList())
                .actions(t.actions().stream()
                        .map(a -> new com.processpuzzle.state.model.ActionRef(a.beanName()).params(a.params()))
                        .toList())
        ).toList();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    // ── Operation layer ────────────────────────────────────────

    public EntityObjectStateView toModel(EntityObjectStateProjection projection) {
        return new EntityObjectStateView(
                projection.objectId(), projection.entityName(), projection.currentStateKey(),
                projection.isFinal(), toAvailableTransitions(projection.availableTransitions()))
                .enteredStateAt(toOffsetDateTime(projection.enteredStateAt()));
    }

    private List<AvailableTransition> toAvailableTransitions(List<AvailableTransitionProjection> projections) {
        return projections.stream()
                .map(p -> new AvailableTransition(p.transitionKey(), p.triggerKey(), p.targetStateKey(), p.guardsSatisfied())
                        .blockedReason(p.blockedReason()))
                .toList();
    }

    public TransitionResult toModel(FireStateTransition.Result result) {
        TransitionOutcome outcome = result.outcome();
        return new TransitionResult(outcome.success(), outcome.previousStateKey())
                .newStateKey(outcome.newStateKey())
                .transitionKey(outcome.transitionKey())
                .executedActions(outcome.executedActions())
                .rejectionReason(outcome.rejectionReason())
                .version(result.version());
    }
}
