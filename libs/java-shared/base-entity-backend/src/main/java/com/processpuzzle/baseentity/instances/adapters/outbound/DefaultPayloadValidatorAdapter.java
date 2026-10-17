package com.processpuzzle.baseentity.instances.adapters.outbound;

import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.common.ValidationException.Violation;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import com.processpuzzle.baseentity.instances.usecases.outbound.PayloadValidatorPort;
import com.processpuzzle.baseentity.port.EntityRulesPort;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Structural-only validation: required-field presence isn't tracked at this level (that lives on
 * the definition side and isn't yet exposed through EntityAttributeView — add it there if you
 * want required-ness enforced here rather than only at form-generation time on the frontend);
 * this adapter's job today is recursing embedded-component sub-payloads against their own
 * definitions via EntityDefinitionLookupPort.
 *
 * <p>Each level — the root and every embedded row — is also checked against the rules of its own
 * definition through {@link EntityRulesPort}. A blocking rule violation is reported with a null
 * {@code attributeCode}: a rule judges the object, not one attribute of it.
 */
@Component
public class DefaultPayloadValidatorAdapter implements PayloadValidatorPort {

    private static final List<String> ADDRESS_TEXT_PARTS = List.of("street", "addressLine2", "postalCode", "city", "region");

    private final EntityDefinitionLookupPort definitionLookupPort;
    private final EntityRulesPort entityRulesPort;

    @Autowired
    public DefaultPayloadValidatorAdapter(EntityDefinitionLookupPort definitionLookupPort, ObjectProvider<EntityRulesPort> entityRulesProvider) {
        this(definitionLookupPort, entityRulesProvider.getIfUnique(() -> EntityRulesPort.NONE));
    }

    public DefaultPayloadValidatorAdapter(EntityDefinitionLookupPort definitionLookupPort, EntityRulesPort entityRulesPort) {
        this.definitionLookupPort = definitionLookupPort;
        this.entityRulesPort = entityRulesPort;
    }

    @Override
    public void validate(String orgKey, EntityDefinitionView definition, Map<String, Object> payload) {
        List<Violation> violations = new ArrayList<>();
        validateInto(orgKey, definition, payload, violations);
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }

    private void validateInto(String orgKey, EntityDefinitionView definition, Map<String, Object> payload, List<Violation> violations) {
        for (EntityAttributeView attribute : definition.attributes()) {
            Object value = payload.get(attribute.code());
            validateAttributePayload(orgKey, definition, attribute, value, violations);
        }
        entityRulesPort.blockingViolationsOf(orgKey, definition.ruleContext(), payload)
            .forEach(message -> violations.add(new Violation(null, message)));
    }

    private void validateAttributePayload(String orgKey, EntityDefinitionView definition, EntityAttributeView attribute, Object value, List<Violation> violations) {
        if (attribute.required() && isBlank(value)) {
            violations.add(new Violation(attribute.code(), "required"));
            return;
        }
        if (value != null && attribute.comments()) {
            validateComments(attribute, value, violations);
            return;
        }
        if (value != null && attribute.address()) {
            validateAddress(attribute, value, violations);
            return;
        }
        if (value != null && attribute.geoLocation()) {
            validateGeoLocation(attribute, value, violations);
            return;
        }
        if (value == null || !attribute.embeddedComponent()) {
            return;
        }

        EntityDefinitionView childDefinition = definitionLookupPort.findByCode(orgKey, attribute.linkedEntityType())
            .orElseThrow(() -> new IllegalStateException(
                "Embedded definition '%s' referenced by '%s.%s' no longer exists"
                    .formatted(attribute.linkedEntityType(), definition.code(), attribute.code())));

        if (attribute.multiValued()) {
            validateMultiValuedEmbedded(orgKey, attribute, value, childDefinition, violations);
        } else {
            validateSingleEmbedded(orgKey, childDefinition, value, violations);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateMultiValuedEmbedded(String orgKey, EntityAttributeView attribute, Object value, EntityDefinitionView childDefinition, List<Violation> violations) {
        if (!(value instanceof List<?> rows)) {
            violations.add(new Violation(attribute.code(), "expected an array of embedded components"));
            return;
        }
        for (Object row : rows) {
            validateInto(orgKey, childDefinition, (Map<String, Object>) row, violations);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateSingleEmbedded(String orgKey, EntityDefinitionView childDefinition, Object value, List<Violation> violations) {
        if (value instanceof Map<?, ?> mapValue) {
            validateInto(orgKey, childDefinition, (Map<String, Object>) mapValue, violations);
        }
    }

    /**
     * Every entry of a COMMENTS attribute is {@code {author, authorId?, createdAt, content}}: a non-blank author, an
     * ISO-8601 instant and Tiptap JSON content. That the stored entries are unchanged is the update use case's check.
     */
    private void validateComments(EntityAttributeView attribute, Object value, List<Violation> violations) {
        if (!(value instanceof List<?> comments)) {
            violations.add(new Violation(attribute.code(), "expected an array of comments"));
            return;
        }
        for (int i = 0; i < comments.size(); i++) {
            String problem = commentProblem(comments.get(i));
            if (problem != null) {
                violations.add(new Violation(attribute.code(), "comment %d %s".formatted(i, problem)));
            }
        }
    }

    private String commentProblem(Object comment) {
        if (!(comment instanceof Map<?, ?> entry)) {
            return "is not an object";
        }
        if (!(entry.get("author") instanceof String author) || author.isBlank()) {
            return "needs an author";
        }
        if (!(entry.get("createdAt") instanceof String createdAt) || !isInstant(createdAt)) {
            return "needs an ISO-8601 createdAt";
        }
        if (!(entry.get("content") instanceof Map<?, ?>)) {
            return "needs rich text content";
        }
        return null;
    }

    /**
     * An ADDRESS value is one object: its text parts strings, its country an ISO 3166-1 alpha-2 code, its
     * coordinates both present or both absent and in range, and {@code locationSource} GEOCODED or MANUAL.
     */
    private void validateAddress(EntityAttributeView attribute, Object value, List<Violation> violations) {
        if (!(value instanceof Map<?, ?> address)) {
            violations.add(new Violation(attribute.code(), "expected an address object"));
            return;
        }
        for (String part : ADDRESS_TEXT_PARTS) {
            Object text = address.get(part);
            if (text != null && !(text instanceof String)) {
                violations.add(new Violation(attribute.code(), "address %s must be text".formatted(part)));
            }
        }
        Object country = address.get("country");
        if (country != null && !(country instanceof String code && code.matches("[A-Z]{2}"))) {
            violations.add(new Violation(attribute.code(), "address country must be an ISO 3166-1 alpha-2 code"));
        }
        Object latitude = address.get("latitude");
        Object longitude = address.get("longitude");
        if ((latitude == null) != (longitude == null)) {
            violations.add(new Violation(attribute.code(), "address latitude and longitude go together"));
        } else if (latitude != null && (!isCoordinate(latitude, 90) || !isCoordinate(longitude, 180))) {
            violations.add(new Violation(attribute.code(), "address latitude must be within 90 and longitude within 180 degrees"));
        }
        Object source = address.get("locationSource");
        if (source != null && !"GEOCODED".equals(source) && !"MANUAL".equals(source)) {
            violations.add(new Violation(attribute.code(), "address locationSource must be GEOCODED or MANUAL"));
        }
    }

    /**
     * A GEO_LOCATION value is one object: both coordinates in range, a non-negative {@code accuracy} in metres and
     * an ISO 8601 {@code capturedAt} instant, the last two optional.
     */
    private void validateGeoLocation(EntityAttributeView attribute, Object value, List<Violation> violations) {
        if (!(value instanceof Map<?, ?> location)) {
            violations.add(new Violation(attribute.code(), "expected a geolocation object"));
            return;
        }
        if (!isCoordinate(location.get("latitude"), 90) || !isCoordinate(location.get("longitude"), 180)) {
            violations.add(new Violation(attribute.code(), "geolocation needs a latitude within 90 and a longitude within 180 degrees"));
        }
        Object accuracy = location.get("accuracy");
        if (accuracy != null && !(accuracy instanceof Number number && Double.isFinite(number.doubleValue()) && number.doubleValue() >= 0)) {
            violations.add(new Violation(attribute.code(), "geolocation accuracy must be a non-negative number of metres"));
        }
        Object capturedAt = location.get("capturedAt");
        if (capturedAt != null && !(capturedAt instanceof String instant && isInstant(instant))) {
            violations.add(new Violation(attribute.code(), "geolocation capturedAt must be an ISO 8601 instant"));
        }
    }

    private boolean isCoordinate(Object value, double bound) {
        return value instanceof Number number && Double.isFinite(number.doubleValue()) && Math.abs(number.doubleValue()) <= bound;
    }

    private boolean isInstant(String value) {
        try {
            Instant.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private boolean isBlank(Object value) {
        return value == null || (value instanceof String s && s.isBlank());
    }
}
