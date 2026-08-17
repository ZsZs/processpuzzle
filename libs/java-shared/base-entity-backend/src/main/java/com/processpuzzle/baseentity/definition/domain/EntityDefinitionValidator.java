package com.processpuzzle.baseentity.definition.domain;

import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.common.ValidationException.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Structural invariants that mirror the frontend's own constructor checks in
 * BaseEntityDescriptor, kept here (as a domain service alongside the entities it validates,
 * not in a usecase) since it operates purely on the entity graph with no outbound port
 * dependency. A bad definition is rejected with a 422 before it ever reaches the frontend
 * rather than throwing at descriptor-construction time there.
 */
@Component
public class EntityDefinitionValidator {

    public void validate(BaseEntityDefinition definition) {
        List<Violation> violations = new ArrayList<>();
        validateEmbeddedParent(definition, violations);
        validateAttributes(definition, violations);
        validateEmbeddedComponentsUniqueness(definition, violations);

        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }

    private void validateEmbeddedParent(BaseEntityDefinition definition, List<Violation> violations) {
        if (definition.isEmbedded() && definition.getComponentParents().isEmpty()) {
            violations.add(new Violation(null,
                "'%s' declares isEmbedded without a componentParent; an embedded component must name the entity whose payload carries it."
                    .formatted(definition.getCode())));
        }
    }

    private void validateAttributes(BaseEntityDefinition definition, List<Violation> violations) {
        for (BaseEntityAttribute attribute : definition.getAttributes()) {
            validateAttribute(attribute, violations);
        }
    }

    private void validateAttribute(BaseEntityAttribute attribute, List<Violation> violations) {
        if (attribute.getFormControlType() == FormControlType.ENUM_SELECT
            && attribute.getValueKind() != ValueKind.ENUM) {
            violations.add(new Violation(attribute.getCode(), "ENUM_SELECT attributes must declare valueKind=ENUM"));
        }
        if (attribute.getValueKind() == ValueKind.ENUM
            && (attribute.getEnumValues() == null || attribute.getEnumValues().isEmpty())) {
            violations.add(new Violation(attribute.getCode(), "valueKind=ENUM requires at least one enumValues entry"));
        }
        if ((attribute.getFormControlType() == FormControlType.FOREIGN_KEY
            || attribute.getFormControlType() == FormControlType.EMBEDDED_COMPONENTS)
            && (attribute.getLinkedEntityType() == null || attribute.getLinkedEntityType().isBlank())) {
            violations.add(new Violation(attribute.getCode(),
                "%s attributes require linkedEntityType".formatted(attribute.getFormControlType())));
        }
    }

    private void validateEmbeddedComponentsUniqueness(BaseEntityDefinition definition, List<Violation> violations) {
        definition.getAttributes().stream()
            .filter(a -> a.getFormControlType() == FormControlType.EMBEDDED_COMPONENTS)
            .collect(Collectors.groupingBy(BaseEntityAttribute::getLinkedEntityType))
            .forEach((childType, attrs) -> {
                if (attrs.size() > 1) {
                    violations.add(new Violation(null,
                        "'%s' declares %d attributes as embedded '%s' components; an embedded child type may be carried by only one attribute"
                            .formatted(definition.getCode(), attrs.size(), childType)));
                }
            });
    }
}
