package com.processpuzzle.baseentity.instances.adapters.outbound;

import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.common.ValidationException.Violation;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import com.processpuzzle.baseentity.instances.usecases.outbound.PayloadValidatorPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Structural-only validation: required-field presence isn't tracked at this level (that lives on
 * the definition side and isn't yet exposed through EntityAttributeView — add it there if you
 * want required-ness enforced here rather than only at form-generation time on the frontend);
 * this adapter's job today is recursing embedded-component sub-payloads against their own
 * definitions via EntityDefinitionLookupPort. A module with base-rule on the classpath should
 * supply its own PayloadValidatorPort bean (@ConditionalOnMissingBean lets that override happen
 * without touching this one).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnMissingBean(PayloadValidatorPort.class)
public class DefaultPayloadValidatorAdapter implements PayloadValidatorPort {

    private final EntityDefinitionLookupPort definitionLookupPort;

    @Override
    public void validate(EntityDefinitionView definition, Map<String, Object> payload) {
        List<Violation> violations = new ArrayList<>();
        validateInto(definition, payload, violations);
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateInto(EntityDefinitionView definition, Map<String, Object> payload, List<Violation> violations) {
        for (EntityAttributeView attribute : definition.attributes()) {
            Object value = payload.get(attribute.code());

            if (attribute.required() && isBlank(value)) {
                violations.add(new Violation(attribute.code(), "required"));
                continue;
            }
            if (value == null || !attribute.embeddedComponent()) {
                continue;
            }

            EntityDefinitionView childDefinition = definitionLookupPort.findByCode(attribute.linkedEntityType())
                .orElseThrow(() -> new IllegalStateException(
                    "Embedded definition '%s' referenced by '%s.%s' no longer exists"
                        .formatted(attribute.linkedEntityType(), definition.code(), attribute.code())));

            if (attribute.multiValued()) {
                if (!(value instanceof List<?> rows)) {
                    violations.add(new Violation(attribute.code(), "expected an array of embedded components"));
                    continue;
                }
                for (Object row : rows) {
                    validateInto(childDefinition, (Map<String, Object>) row, violations);
                }
            } else {
                validateInto(childDefinition, (Map<String, Object>) value, violations);
            }
        }
    }

    private boolean isBlank(Object value) {
        return value == null || (value instanceof String s && s.isBlank());
    }
}
