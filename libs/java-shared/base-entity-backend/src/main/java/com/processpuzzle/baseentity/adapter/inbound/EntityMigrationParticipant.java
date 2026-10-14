package com.processpuzzle.baseentity.adapter.inbound;

import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.usecases.outbound.EntityInstanceDataPort;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.MigrationParticipant;
import com.processpuzzle.core.definition.MigrationStep;
import com.processpuzzle.core.definition.MigrationStepFailedException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Migrates entity objects so that they fit the entity definitions a release carries. Three operations, each about one
 * attribute, the step's subject:
 * <ul>
 *   <li>{@value #RENAME_ATTRIBUTE} — moves each object's value of the released attribute {@code subject} to the draft's
 *   attribute {@code to}. Refused while some object already holds a value for {@code to}.</li>
 *   <li>{@value #FILL_ATTRIBUTE} — sets {@code value} on each object; with {@code only: empty}, the default, only where
 *   the object has none.</li>
 *   <li>{@value #MAP_VALUES} — replaces each value found among the keys of {@code mapping} with the value it maps to,
 *   element by element for a multi-valued attribute. A value not in {@code mapping} is kept, or with
 *   {@code unmapped: fail} refuses the step. A state machine's state attribute is an attribute like any other, so this
 *   also moves objects out of a removed or renamed state.</li>
 * </ul>
 */
@Component
public class EntityMigrationParticipant implements MigrationParticipant {

    static final String RENAME_ATTRIBUTE = "renameAttribute";
    static final String FILL_ATTRIBUTE = "fillAttribute";
    static final String MAP_VALUES = "mapValues";

    private final EntityDefinitionRepository repository;
    private final EntityInstanceDataPort instances;

    public EntityMigrationParticipant(EntityDefinitionRepository repository, EntityInstanceDataPort instances) {
        this.repository = repository;
        this.instances = instances;
    }

    @Override
    public String kind() {
        return DefinitionKinds.ENTITY;
    }

    @Override
    public Set<String> operations() {
        return Set.of(RENAME_ATTRIBUTE, FILL_ATTRIBUTE, MAP_VALUES);
    }

    @Override
    public Optional<String> unusable(String orgKey, MigrationStep step) {
        Optional<BaseEntityDefinition> released = repository.findByOrgKeyAndCodeAndChannel(orgKey, step.key(), Channel.RELEASED);
        Optional<BaseEntityDefinition> draft = repository.findByOrgKeyAndCodeAndChannel(orgKey, step.key(), Channel.DRAFT);
        if (released.isEmpty() || draft.isEmpty()) {
            return Optional.of("'%s' is not both released and drafted, so it has no objects to migrate to a draft."
                .formatted(step.key()));
        }
        Optional<BaseEntityAttribute> before = attribute(released.get(), step.subject());
        Optional<BaseEntityAttribute> after = attribute(draft.get(), step.subject());
        return switch (step.operation()) {
            case RENAME_ATTRIBUTE -> renameUnusable(step, before, after, draft.get(), released.get());
            case FILL_ATTRIBUTE -> fillUnusable(step, after);
            case MAP_VALUES -> mapUnusable(step, after);
            default -> Optional.of("'%s' is not an operation on entity objects.".formatted(step.operation()));
        };
    }

    private Optional<String> renameUnusable(MigrationStep step, Optional<BaseEntityAttribute> before,
                                            Optional<BaseEntityAttribute> after, BaseEntityDefinition draft,
                                            BaseEntityDefinition released) {
        String to = text(step.parameters().get("to"));
        if (before.isEmpty() || after.isPresent()) {
            return Optional.of("'%s.%s' must be released and removed from the draft to be renamed."
                .formatted(step.key(), step.subject()));
        }
        if (to == null || attribute(draft, to).isEmpty() || attribute(released, to).isPresent()) {
            return Optional.of("'%s.%s' can only be renamed to an attribute the draft adds.".formatted(step.key(), step.subject()));
        }
        return Optional.empty();
    }

    private Optional<String> fillUnusable(MigrationStep step, Optional<BaseEntityAttribute> after) {
        if (after.isEmpty()) {
            return Optional.of("'%s.%s' is not an attribute of the draft.".formatted(step.key(), step.subject()));
        }
        Object value = step.parameters().get("value");
        if (StoredValues.isBlank(value) || !StoredValues.fits(value, after.get().getValueKind(), after.get().isMultiValued())) {
            return Optional.of("'%s.%s' cannot be filled with %s.".formatted(step.key(), step.subject(), value));
        }
        return Optional.empty();
    }

    private Optional<String> mapUnusable(MigrationStep step, Optional<BaseEntityAttribute> after) {
        if (after.isEmpty()) {
            return Optional.of("'%s.%s' is not an attribute of the draft.".formatted(step.key(), step.subject()));
        }
        Map<String, Object> mapping = mapping(step);
        if (mapping.isEmpty()) {
            return Optional.of("Mapping the values of '%s.%s' needs at least one value to map.".formatted(step.key(), step.subject()));
        }
        boolean fits = mapping.values().stream()
            .allMatch(value -> value != null && StoredValues.fits(value, after.get().getValueKind(), false));
        return fits ? Optional.empty()
            : Optional.of("'%s.%s' would get values that do not fit it.".formatted(step.key(), step.subject()));
    }

    @Override
    public long apply(String orgKey, MigrationStep step) {
        return switch (step.operation()) {
            case RENAME_ATTRIBUTE -> rename(orgKey, step);
            case FILL_ATTRIBUTE -> fill(orgKey, step);
            case MAP_VALUES -> map(orgKey, step);
            default -> throw new MigrationStepFailedException("'%s' is not an operation on entity objects.".formatted(step.operation()));
        };
    }

    private long rename(String orgKey, MigrationStep step) {
        String from = step.subject();
        String to = text(step.parameters().get("to"));
        long clashes = instances.payloadsOf(orgKey, step.key()).stream()
            .filter(payload -> !StoredValues.isBlank(payload.get(from)) && !StoredValues.isBlank(payload.get(to)))
            .count();
        if (clashes > 0) {
            throw new MigrationStepFailedException("%d object(s) of '%s' hold values for both '%s' and '%s'."
                .formatted(clashes, step.key(), from, to));
        }
        return instances.rewritePayloads(orgKey, step.key(), payload -> {
            if (payload.containsKey(from)) {
                Object value = payload.remove(from);
                if (!StoredValues.isBlank(value)) {
                    payload.put(to, value);
                }
            }
            return payload;
        });
    }

    private long fill(String orgKey, MigrationStep step) {
        Object value = step.parameters().get("value");
        boolean always = "always".equals(step.parameters().get("only"));
        return instances.rewritePayloads(orgKey, step.key(), payload -> {
            if (always || StoredValues.isBlank(payload.get(step.subject()))) {
                payload.put(step.subject(), value);
            }
            return payload;
        });
    }

    private long map(String orgKey, MigrationStep step) {
        Map<String, Object> mapping = mapping(step);
        if ("fail".equals(step.parameters().get("unmapped"))) {
            long unmapped = instances.payloadsOf(orgKey, step.key()).stream()
                .map(payload -> payload.get(step.subject()))
                .filter(value -> !StoredValues.isBlank(value))
                .filter(value -> elements(value).stream().anyMatch(element -> !mapping.containsKey(String.valueOf(element))))
                .count();
            if (unmapped > 0) {
                throw new MigrationStepFailedException("%d object(s) of '%s' hold values of '%s' the mapping does not cover."
                    .formatted(unmapped, step.key(), step.subject()));
            }
        }
        return instances.rewritePayloads(orgKey, step.key(), payload -> {
            Object value = payload.get(step.subject());
            if (value instanceof Collection<?> values) {
                payload.put(step.subject(), values.stream().map(element -> mapped(mapping, element)).toList());
            } else if (value != null) {
                payload.put(step.subject(), mapped(mapping, value));
            }
            return payload;
        });
    }

    private static Object mapped(Map<String, Object> mapping, Object value) {
        return mapping.getOrDefault(String.valueOf(value), value);
    }

    private static Collection<?> elements(Object value) {
        return value instanceof Collection<?> values ? values : List.of(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapping(MigrationStep step) {
        return step.parameters().get("mapping") instanceof Map<?, ?> mapping ? (Map<String, Object>) mapping : Map.of();
    }

    private static Optional<BaseEntityAttribute> attribute(BaseEntityDefinition definition, String code) {
        return definition.getAttributes().stream().filter(attribute -> attribute.getCode().equals(code)).findFirst();
    }

    private static String text(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }
}
