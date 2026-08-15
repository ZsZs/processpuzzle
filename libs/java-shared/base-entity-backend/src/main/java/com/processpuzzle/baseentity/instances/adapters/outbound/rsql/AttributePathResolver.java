package com.processpuzzle.baseentity.instances.adapters.outbound.rsql;

import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import java.util.ArrayList;
import java.util.List;
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

    public ResolvedAttributePath resolve(String rootEntityDefinitionCode, String dottedSelector) {
        String[] parts = dottedSelector.split("\\.");
        String currentDefinitionCode = rootEntityDefinitionCode;
        List<PathSegment> segments = new ArrayList<>();
        EntityAttributeView.ValueKindView leafValueKind = null;

        for (int i = 0; i < parts.length; i++) {
            String attributeCode = parts[i];
            boolean lastSegment = i == parts.length - 1;
            String definitionCode = currentDefinitionCode;

            EntityDefinitionView definition = definitionLookupPort.findByCode(definitionCode)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Unknown entity definition '%s' while resolving '%s'".formatted(definitionCode, dottedSelector)));

            EntityAttributeView attribute = definition.attribute(attributeCode);
            if (attribute == null) {
                throw new IllegalArgumentException(
                    "'%s' has no attribute '%s' (resolving '%s')".formatted(definitionCode, attributeCode, dottedSelector));
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
}
