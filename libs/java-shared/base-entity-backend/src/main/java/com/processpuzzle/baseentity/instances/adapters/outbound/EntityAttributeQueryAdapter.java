package com.processpuzzle.baseentity.instances.adapters.outbound;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.baseentity.api.EntityAttributeKind;
import com.processpuzzle.baseentity.api.EntityAttributeQuery;
import com.processpuzzle.baseentity.api.EntityTypeDescription;
import com.processpuzzle.baseentity.api.EntityTypeDescription.AttributeDescription;
import com.processpuzzle.baseentity.instances.usecases.outbound.MeasureView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implements the module's published {@link EntityAttributeQuery} over the existing {@link
 * EntityDefinitionLookupPort}, rather than reaching into the definition module a second time: that
 * port already maps a {@code BaseEntityDefinition} down to a view, and a second path to the same
 * metadata would be a second thing to keep in step.
 */
@Component
@RequiredArgsConstructor
public class EntityAttributeQueryAdapter implements EntityAttributeQuery {

    private final EntityDefinitionLookupPort definitionLookupPort;

    @Override
    public Optional<EntityAttributeKind> attributeKind(String orgKey, String entityDefinitionCode, String attributeCode,
                                                       Channel channel) {
        return definitionLookupPort.findByCode(orgKey, entityDefinitionCode, channel)
            .map(definition -> definition.attribute(attributeCode))
            .map(EntityAttributeView::valueKind)
            // valueOf, not a switch: the two enums are kept in step by name, so a kind added to one
            // side only fails here loudly instead of being silently mapped to something plausible.
            .map(kind -> EntityAttributeKind.valueOf(kind.name()));
    }

    @Override
    public boolean entityTypeExists(String orgKey, String entityDefinitionCode, Channel channel) {
        return definitionLookupPort.findByCode(orgKey, entityDefinitionCode, channel).isPresent();
    }

    @Override
    public Optional<String> titleAttribute(String orgKey, String entityDefinitionCode) {
        // A label shown to users of the running application, so the released definition's.
        return definitionLookupPort.findByCode(orgKey, entityDefinitionCode, Channel.RELEASED)
            .map(EntityDefinitionView::titleAttribute);
    }

    @Override
    public Optional<EntityTypeDescription> describe(String orgKey, String entityDefinitionCode, Channel channel) {
        return definitionLookupPort.findByCode(orgKey, entityDefinitionCode, channel).map(EntityAttributeQueryAdapter::describe);
    }

    @Override
    public Optional<EntityTypeDescription> describeByRuleContext(String orgKey, String ruleContext) {
        return definitionLookupPort.findByRuleContext(orgKey, ruleContext, Channel.RELEASED)
            .or(() -> definitionLookupPort.findByRuleContext(orgKey, ruleContext, Channel.DRAFT))
            .map(EntityAttributeQueryAdapter::describe);
    }

    @Override
    public List<EntityTypeDescription> describeAll(String orgKey) {
        List<EntityDefinitionView> released = definitionLookupPort.findAll(orgKey, Channel.RELEASED);
        Set<String> codes = released.stream().map(EntityDefinitionView::code).collect(Collectors.toSet());
        return Stream.concat(released.stream(),
                definitionLookupPort.findAll(orgKey, Channel.DRAFT).stream().filter(draft -> !codes.contains(draft.code())))
            .map(EntityAttributeQueryAdapter::describe)
            .sorted(Comparator.comparing(EntityTypeDescription::code))
            .toList();
    }

    private static EntityTypeDescription describe(EntityDefinitionView definition) {
        return new EntityTypeDescription(definition.code(), definition.ruleContext(), definition.embedded(),
            definition.attributes().stream().map(EntityAttributeQueryAdapter::describe).toList());
    }

    private static AttributeDescription describe(EntityAttributeView attribute) {
        MeasureView measure = attribute.measure();
        return new AttributeDescription(attribute.code(), EntityAttributeKind.valueOf(attribute.valueKind().name()),
            attribute.required(), attribute.multiValued(), attribute.embeddedComponent(), attribute.linkedEntityType(),
            measure == null ? null : measure.kind().name(), measure == null ? List.of() : optionsOf(measure));
    }

    /** What a measured value may use: the units or currencies the attribute allows, else its default one. */
    private static List<String> optionsOf(MeasureView measure) {
        if (measure.allowedUnits() != null && !measure.allowedUnits().isEmpty()) {
            return measure.allowedUnits();
        }
        if (measure.allowedCurrencies() != null && !measure.allowedCurrencies().isEmpty()) {
            return measure.allowedCurrencies();
        }
        String single = measure.defaultUnit() != null ? measure.defaultUnit() : measure.defaultCurrency();
        return single == null ? List.of() : List.of(single);
    }
}
