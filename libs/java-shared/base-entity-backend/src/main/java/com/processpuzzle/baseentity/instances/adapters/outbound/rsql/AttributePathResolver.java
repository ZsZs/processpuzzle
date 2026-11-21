package com.processpuzzle.baseentity.instances.adapters.outbound.rsql;

import com.processpuzzle.baseentity.instances.domain.MeasureValues;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import com.processpuzzle.baseentity.instances.usecases.outbound.MeasureView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves a dotted RSQL selector (e.g. {@code address.city}) against a chain of definition
 * metadata reached exclusively through EntityDefinitionLookupPort — no direct dependency on the
 * definition module's JPA entities, which is the whole point of the module boundary. Only
 * embedded-component attributes are traversable mid-path; a FOREIGN_KEY attribute points at data
 * that lives in a different EntityObject row, not inside this payload.
 */
@Component
@RequiredArgsConstructor
public class AttributePathResolver {

    private final EntityDefinitionLookupPort definitionLookupPort;

    public ResolvedAttributePath resolve(String orgKey, String rootEntityDefinitionCode, String dottedSelector) {
        String[] parts = dottedSelector.split("\\.");
        String currentDefinitionCode = rootEntityDefinitionCode;
        List<PathSegment> segments = new ArrayList<>();
        EntityAttributeView.ValueKindView leafValueKind = null;

        for (int i = 0; i < parts.length; i++) {
            String attributeCode = parts[i];
            boolean lastSegment = i == parts.length - 1;
            String definitionCode = currentDefinitionCode;

            EntityDefinitionView definition = definitionLookupPort.findByCode(orgKey, definitionCode)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Unknown entity definition '%s' while resolving '%s'".formatted(definitionCode, dottedSelector)));

            EntityAttributeView attribute = definition.attribute(attributeCode);
            if (attribute == null) {
                throw new IllegalArgumentException(
                    "'%s' has no attribute '%s' (resolving '%s')".formatted(definitionCode, attributeCode, dottedSelector));
            }

            if (attribute.measure() != null) {
                return measurePath(segments, attribute, parts, i, dottedSelector);
            }

            if (!lastSegment) {
                if (!attribute.embeddedComponent() || attribute.linkedEntityType() == null) {
                    throw new IllegalArgumentException(
                        "'%s.%s' is not an embedded-component attribute; only embedded components can appear mid-path"
                            .formatted(definitionCode, attributeCode));
                }
                segments.add(new PathSegment(attributeCode, attribute.multiValued()));
                currentDefinitionCode = attribute.linkedEntityType();
            } else {
                boolean arrayLeaf = attribute.embeddedComponent() && attribute.multiValued();
                segments.add(new PathSegment(attributeCode, arrayLeaf));
                leafValueKind = attribute.valueKind();
            }
        }

        return new ResolvedAttributePath(segments, leafValueKind);
    }

    /**
     * A QUANTITY, MONEY or DIMENSIONS attribute is queried through the numbers the backend derives from it on every
     * write — {@code weight.baseAmount=gt=5} — never as the object itself. They compare as numbers, in the kind's base unit
     * (kilograms for a mass, metres for a length, square and cubic metres for dimensions), whatever unit the value was
     * entered in; the attribute's own unit and amount are not selectable.
     */
    private ResolvedAttributePath measurePath(List<PathSegment> segments, EntityAttributeView attribute, String[] parts, int index, String selector) {
        List<String> derived = derivedNumbersOf(attribute.measure());
        boolean selectsDerived = index == parts.length - 2 && derived.contains(parts[index + 1]);
        if (!selectsDerived) {
            throw new IllegalArgumentException("'%s' is a %s; select one of its numbers: %s (resolving '%s')".formatted(
                attribute.code(), attribute.measure().kind().name().toLowerCase(Locale.ROOT),
                derived.stream().map(name -> attribute.code() + "." + name).collect(Collectors.joining(", ")), selector));
        }
        segments.add(new PathSegment(attribute.code(), false));
        segments.add(new PathSegment(parts[index + 1], false));
        return new ResolvedAttributePath(segments, EntityAttributeView.ValueKindView.NUMBER);
    }

    private static List<String> derivedNumbersOf(MeasureView measure) {
        return switch (measure.kind()) {
            case QUANTITY, MONEY -> List.of(MeasureValues.BASE_AMOUNT);
            case DIMENSIONS -> measure.threeDimensional() ? List.of(MeasureValues.BASE_AREA, MeasureValues.BASE_VOLUME) : List.of(MeasureValues.BASE_AREA);
        };
    }
}
