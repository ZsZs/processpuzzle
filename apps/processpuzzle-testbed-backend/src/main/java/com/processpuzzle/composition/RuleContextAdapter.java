package com.processpuzzle.composition;

import com.processpuzzle.baseentity.api.EntityAttributeQuery;
import com.processpuzzle.baseentity.api.EntityTypeDescription;
import com.processpuzzle.baseentity.api.EntityTypeDescription.AttributeDescription;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.rule.port.RuleContext;
import com.processpuzzle.rule.port.RuleContext.ContextAttribute;
import com.processpuzzle.rule.port.RuleContextDirectory;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Tells base-rule what the entity a rule is written for looks like, from base-entity's definitions. The entity is found by
 * the rule's {@code context}, its display name. A component embedded in it is expanded in place, as a rule reads it as part
 * of {@code entity}; expansion stops at a type already on the way down, which an entity that embeds itself would otherwise
 * never leave, and at a fixed depth.
 */
@Component
public class RuleContextAdapter implements RuleContextDirectory {

    static final int MAX_DEPTH = 3;

    private final EntityAttributeQuery entities;

    public RuleContextAdapter(EntityAttributeQuery entities) {
        this.entities = entities;
    }

    @Override
    public Optional<RuleContext> find(String orgKey, String context) {
        return entities.describeByRuleContext(orgKey, context)
            .map(type -> new RuleContext(type.name(), attributesOf(orgKey, type, Set.of(type.code()), 1)));
    }

    private List<ContextAttribute> attributesOf(String orgKey, EntityTypeDescription type, Set<String> path, int depth) {
        return type.attributes().stream().map(attribute -> toContext(orgKey, attribute, path, depth)).toList();
    }

    private ContextAttribute toContext(String orgKey, AttributeDescription attribute, Set<String> path, int depth) {
        List<ContextAttribute> children = attribute.embedded() ? childrenOf(orgKey, attribute, path, depth) : List.of();
        return new ContextAttribute(attribute.code(), attribute.kind().name(), attribute.required(), attribute.multiValued(),
            attribute.embedded(), attribute.measure(), attribute.measureOptions(), children);
    }

    private List<ContextAttribute> childrenOf(String orgKey, AttributeDescription attribute, Set<String> path, int depth) {
        String code = attribute.linkedEntityType();
        if (code == null || depth >= MAX_DEPTH || path.contains(code)) {
            return List.of();
        }
        Set<String> deeper = new HashSet<>(path);
        deeper.add(code);
        return entities.describe(orgKey, code, Channel.RELEASED)
            .or(() -> entities.describe(orgKey, code, Channel.DRAFT))
            .map(child -> attributesOf(orgKey, child, deeper, depth + 1))
            .orElse(List.of());
    }
}
