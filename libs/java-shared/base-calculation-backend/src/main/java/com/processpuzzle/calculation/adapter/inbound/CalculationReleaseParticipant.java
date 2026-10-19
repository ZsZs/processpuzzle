package com.processpuzzle.calculation.adapter.inbound;

import com.processpuzzle.calculation.domain.CalculationDefinition;
import com.processpuzzle.calculation.domain.CalculationDefinitionRepository;
import com.processpuzzle.calculation.domain.definition.CalculationModel;
import com.processpuzzle.calculation.usecase.CalculationValidator;
import com.processpuzzle.calculation.usecase.CalculationsReleased;
import com.processpuzzle.core.definition.CanonicalJson;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseCandidate;
import com.processpuzzle.core.definition.ReleaseParticipant;
import com.processpuzzle.core.definition.ReleaseProblem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Releases calculations: copies each draft onto its released row — the definition version with it — and deletes the
 * released rows whose draft was deleted. Triggers run the released calculations only.
 *
 * <p>A calculation refers to every entity type it reads or writes: its entity parameters', its query inputs', its
 * output's and its triggers'. A draft that no longer validates — an expression an engine upgrade rejects — is
 * refused.
 *
 * <p>Announces what it released as {@link CalculationsReleased}, after which {@link CalculationsReleasedListener}
 * catches up the freezes the released calculations would have made.
 */
@Component
public class CalculationReleaseParticipant implements ReleaseParticipant {

    private final CalculationDefinitionRepository repository;
    private final CalculationValidator validator;
    private final ApplicationEventPublisher publisher;

    public CalculationReleaseParticipant(CalculationDefinitionRepository repository, CalculationValidator validator,
                                         ApplicationEventPublisher publisher) {
        this.repository = repository;
        this.validator = validator;
        this.publisher = publisher;
    }

    @Override
    public String kind() {
        return DefinitionKinds.CALCULATION;
    }

    @Override
    public int order() {
        return DefinitionKinds.CALCULATION_ORDER;
    }

    @Override
    public Set<String> drafts(String orgKey) {
        return byId(orgKey, Channel.DRAFT).keySet();
    }

    @Override
    public List<PendingChange> pending(String orgKey) {
        Map<String, CalculationDefinition> drafts = byId(orgKey, Channel.DRAFT);
        Map<String, CalculationDefinition> released = byId(orgKey, Channel.RELEASED);
        Set<String> ids = new TreeSet<>(drafts.keySet());
        ids.addAll(released.keySet());
        List<PendingChange> pending = new ArrayList<>();
        for (String id : ids) {
            PendingChange change = PendingChange.between(kind(), id, fingerprint(drafts.get(id)), fingerprint(released.get(id)));
            if (change != null) {
                pending.add(change);
            }
        }
        return pending;
    }

    @Override
    public List<DefinitionReference> references(String orgKey) {
        List<DefinitionReference> references = new ArrayList<>();
        for (CalculationDefinition definition : byId(orgKey, Channel.DRAFT).values()) {
            entitiesOf(definition.parsedModel()).forEach(entity ->
                    references.add(DefinitionReference.required(kind(), definition.getId(), DefinitionKinds.ENTITY, entity)));
        }
        return references;
    }

    @Override
    public List<ReleaseProblem> check(String orgKey, ReleaseCandidate candidate) {
        List<ReleaseProblem> problems = new ArrayList<>();
        for (CalculationDefinition definition : byId(orgKey, Channel.DRAFT).values()) {
            CalculationValidator.Validation validation = validator.validate(definition.getSource());
            validation.errors().forEach(error -> problems.add(
                    ReleaseProblem.error(kind(), definition.getId(), "calculation.invalid", error)));
        }
        return problems;
    }

    @Override
    public void release(String orgKey) {
        release(orgKey, byId(orgKey, Channel.DRAFT).keySet(), true);
    }

    /** Releases only the drafts of these ids: what a seed loader does with what it wrote. */
    @Transactional
    public void release(String orgKey, Collection<String> ids) {
        release(orgKey, ids, false);
    }

    private void release(String orgKey, Collection<String> ids, boolean removeTheRest) {
        Map<String, CalculationDefinition> drafts = byId(orgKey, Channel.DRAFT);
        Map<String, CalculationDefinition> released = byId(orgKey, Channel.RELEASED);
        Set<String> releasedIds = new LinkedHashSet<>();
        for (String id : ids) {
            CalculationDefinition draft = drafts.get(id);
            if (draft == null) {
                continue;
            }
            CalculationDefinition current = released.get(id);
            if (current == null) {
                repository.save(CalculationDefinition.releasedCopyOf(draft));
            } else {
                current.replaceWith(draft);
                repository.save(current);
            }
            releasedIds.add(id);
        }
        if (removeTheRest) {
            repository.deleteAll(released.values().stream().filter(row -> !drafts.containsKey(row.getId())).toList());
        }
        if (!releasedIds.isEmpty()) {
            publisher.publishEvent(new CalculationsReleased(orgKey, releasedIds));
        }
    }

    static Set<String> entitiesOf(CalculationModel model) {
        Set<String> entities = new LinkedHashSet<>();
        model.parameters().values().stream().filter(parameter -> parameter != null && parameter.isEntity())
                .forEach(parameter -> entities.add(parameter.entity()));
        model.inputs().values().stream().filter(input -> input != null && input.entity() != null)
                .forEach(input -> entities.add(input.entity()));
        if (model.output() != null && model.output().entity() != null) {
            entities.add(model.output().entity());
        }
        if (model.trigger() != null) {
            if (model.trigger().onChange() != null) {
                entities.add(model.trigger().onChange().entity());
            }
            if (model.trigger().state() != null) {
                entities.add(model.trigger().state().entity());
            }
            if (model.trigger().freeze() != null && model.trigger().freeze().state() != null) {
                entities.add(model.trigger().freeze().state().entity());
            }
        }
        entities.remove(null);
        return entities;
    }

    private Map<String, CalculationDefinition> byId(String orgKey, Channel channel) {
        return repository.findByOrgKeyAndChannelOrderByIdAsc(orgKey, channel).stream()
                .collect(Collectors.toMap(CalculationDefinition::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    /** The authored content only — the source; neither the channel, nor the versions and timestamps. */
    static String fingerprint(CalculationDefinition definition) {
        return definition == null ? null : CanonicalJson.of(Map.of("source", definition.getSource()), Set.of());
    }
}
