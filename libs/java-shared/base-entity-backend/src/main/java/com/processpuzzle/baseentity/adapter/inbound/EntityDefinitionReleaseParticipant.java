package com.processpuzzle.baseentity.adapter.inbound;

import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.usecases.outbound.EntityInstanceExistenceCheckPort;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseCandidate;
import com.processpuzzle.core.definition.ReleaseParticipant;
import com.processpuzzle.core.definition.ReleaseProblem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Releases entity definitions: copies each draft onto its released row, and deletes the released rows whose draft
 * was deleted. The running application validates and stores entity objects against the released row only.
 *
 * <p><strong>What it refuses.</strong> Objects are not migrated from one release to the next, so a change the
 * existing objects of a type would no longer fit is an error while such objects exist:
 * <ul>
 *   <li>a definition removed;</li>
 *   <li>a required attribute added without a default — no existing object has a value for it;</li>
 *   <li>an attribute whose value kind or multiplicity changes — the stored values are of the old shape;</li>
 *   <li>a definition switching between embedded and stand-alone — its objects are stored the other way.</li>
 * </ul>
 * Warnings, which a designer may release past: an attribute removed (its stored values stay, unread), and an
 * attribute that becomes required (existing objects without a value cannot be saved until one is given).
 * A rename is a removal and an addition, and is judged as such.
 */
@Component
public class EntityDefinitionReleaseParticipant implements ReleaseParticipant {

    private final EntityDefinitionRepository repository;
    private final EntityInstanceExistenceCheckPort instances;
    private final EntityDefinitionFingerprint fingerprint;

    public EntityDefinitionReleaseParticipant(EntityDefinitionRepository repository,
                                              EntityInstanceExistenceCheckPort instances,
                                              EntityDefinitionFingerprint fingerprint) {
        this.repository = repository;
        this.instances = instances;
        this.fingerprint = fingerprint;
    }

    @Override
    public String kind() {
        return DefinitionKinds.ENTITY;
    }

    @Override
    public int order() {
        return DefinitionKinds.ENTITY_ORDER;
    }

    @Override
    public Set<String> drafts(String orgKey) {
        return byCode(orgKey, Channel.DRAFT).keySet();
    }

    /**
     * Each draft's code, and its name too: a definition is keyed by its code, but the rest of the platform — an app's
     * routes, a state machine, the screens — names the entity type by its name.
     */
    @Override
    public Set<String> referableKeys(String orgKey) {
        Set<String> keys = new TreeSet<>();
        for (BaseEntityDefinition draft : byCode(orgKey, Channel.DRAFT).values()) {
            keys.add(draft.getCode());
            if (draft.getName() != null && !draft.getName().isBlank()) {
                keys.add(draft.getName());
            }
        }
        return keys;
    }

    @Override
    public List<PendingChange> pending(String orgKey) {
        Map<String, BaseEntityDefinition> drafts = byCode(orgKey, Channel.DRAFT);
        Map<String, BaseEntityDefinition> released = byCode(orgKey, Channel.RELEASED);
        Set<String> codes = new TreeSet<>(drafts.keySet());
        codes.addAll(released.keySet());

        List<PendingChange> pending = new ArrayList<>();
        for (String code : codes) {
            PendingChange change = PendingChange.between(kind(), code,
                fingerprint.of(drafts.get(code)), fingerprint.of(released.get(code)));
            if (change != null) {
                pending.add(change);
            }
        }
        return pending;
    }

    @Override
    public List<DefinitionReference> references(String orgKey) {
        List<DefinitionReference> references = new ArrayList<>();
        for (BaseEntityDefinition draft : byCode(orgKey, Channel.DRAFT).values()) {
            draft.getComponentParents().forEach(parent ->
                references.add(DefinitionReference.required(kind(), draft.getCode(), DefinitionKinds.ENTITY, parent)));
            draft.getAttributes().stream()
                .map(BaseEntityAttribute::getLinkedEntityType)
                .filter(linked -> linked != null && !linked.isBlank())
                .distinct()
                .forEach(linked ->
                    references.add(DefinitionReference.required(kind(), draft.getCode(), DefinitionKinds.ENTITY, linked)));
        }
        return references;
    }

    @Override
    public List<ReleaseProblem> check(String orgKey, ReleaseCandidate candidate) {
        Map<String, BaseEntityDefinition> drafts = byCode(orgKey, Channel.DRAFT);
        Map<String, BaseEntityDefinition> released = byCode(orgKey, Channel.RELEASED);
        List<ReleaseProblem> problems = new ArrayList<>();
        for (BaseEntityDefinition current : released.values()) {
            BaseEntityDefinition draft = drafts.get(current.getCode());
            if (Objects.equals(fingerprint.of(draft), fingerprint.of(current))
                || !instances.existsAnyInstanceOf(orgKey, current.getCode())) {
                continue;
            }
            if (draft == null) {
                problems.add(ReleaseProblem.error(kind(), current.getCode(), "entity.release.removedWithObjects",
                    "'%s' still has objects; delete them before releasing its removal.".formatted(current.getCode())));
            } else {
                problems.addAll(attributeProblems(current, draft));
            }
        }
        return problems;
    }

    /** What changing {@code current} into {@code draft} would do to the objects that already exist. */
    private List<ReleaseProblem> attributeProblems(BaseEntityDefinition current, BaseEntityDefinition draft) {
        String code = current.getCode();
        List<ReleaseProblem> problems = new ArrayList<>();
        if (current.isEmbedded() != draft.isEmbedded()) {
            problems.add(ReleaseProblem.error(kind(), code, "entity.release.embeddingChanged",
                "'%s' cannot switch between embedded and stand-alone while it has objects.".formatted(code)));
        }
        Map<String, BaseEntityAttribute> before = attributesOf(current);
        Map<String, BaseEntityAttribute> after = attributesOf(draft);
        before.forEach((attributeCode, old) -> {
            BaseEntityAttribute changed = after.get(attributeCode);
            if (changed == null) {
                problems.add(ReleaseProblem.warning(kind(), code, "entity.release.attributeRemoved",
                    "'%s.%s' is removed; the values its objects hold stay stored but are no longer shown."
                        .formatted(code, attributeCode)));
            } else if (old.getValueKind() != changed.getValueKind() || old.isMultiValued() != changed.isMultiValued()) {
                problems.add(ReleaseProblem.error(kind(), code, "entity.release.attributeTypeChanged",
                    "'%s.%s' changes its type while objects hold values of the old one.".formatted(code, attributeCode)));
            } else if (!old.isRequired() && changed.isRequired() && changed.getDefaultValue() == null) {
                problems.add(ReleaseProblem.warning(kind(), code, "entity.release.attributeNowRequired",
                    "'%s.%s' becomes required; objects without a value cannot be saved until they get one."
                        .formatted(code, attributeCode)));
            }
        });
        after.forEach((attributeCode, added) -> {
            if (!before.containsKey(attributeCode) && added.isRequired() && added.getDefaultValue() == null) {
                problems.add(ReleaseProblem.error(kind(), code, "entity.release.requiredWithoutDefault",
                    "'%s.%s' is required and has no default, but existing objects have no value for it."
                        .formatted(code, attributeCode)));
            }
        });
        return problems;
    }

    @Override
    public void release(String orgKey) {
        Map<String, BaseEntityDefinition> drafts = byCode(orgKey, Channel.DRAFT);
        Map<String, BaseEntityDefinition> released = byCode(orgKey, Channel.RELEASED);
        drafts.forEach((code, draft) -> {
            BaseEntityDefinition current = released.get(code);
            if (current == null) {
                repository.save(BaseEntityDefinition.releasedCopyOf(draft));
            } else {
                current.releaseFrom(draft);
                repository.save(current);
            }
        });
        released.forEach((code, current) -> {
            if (!drafts.containsKey(code)) {
                repository.delete(current);
            }
        });
    }

    private Map<String, BaseEntityDefinition> byCode(String orgKey, Channel channel) {
        return repository.findAllByOrgKeyAndChannel(orgKey, channel).stream()
            .collect(Collectors.toMap(BaseEntityDefinition::getCode, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private static Map<String, BaseEntityAttribute> attributesOf(BaseEntityDefinition definition) {
        return definition.getAttributes().stream()
            .collect(Collectors.toMap(BaseEntityAttribute::getCode, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }
}
