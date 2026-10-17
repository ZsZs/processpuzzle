package com.processpuzzle.baseentity.instances.adapters.outbound;

import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView.ValueKindView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import com.processpuzzle.baseentity.port.EntityRulesPort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultPayloadValidatorAdapterTest {

    private static final String ORG = "acme";

    @Mock
    private EntityDefinitionLookupPort definitionLookupPort;

    private DefaultPayloadValidatorAdapter validator;

    @BeforeEach
    void setUp() {
        validator = new DefaultPayloadValidatorAdapter(definitionLookupPort, EntityRulesPort.NONE);
    }

    @Test
    void validate_validPayload_passes() {
        EntityDefinitionView def = new EntityDefinitionView(
                "partner",
                false,
                List.of(
                        new EntityAttributeView("name", ValueKindView.TEXT, false, false, null, true),
                        new EntityAttributeView("website", ValueKindView.TEXT, false, false, null, false)
                )
        );

        Map<String, Object> payload = Map.of("name", "ACME Corp");

        validator.validate(ORG, def, payload);
    }

    @Test
    void validate_missingRequiredField_throwsValidationException() {
        EntityDefinitionView def = new EntityDefinitionView(
                "partner",
                false,
                List.of(
                        new EntityAttributeView("name", ValueKindView.TEXT, false, false, null, true)
                )
        );

        Map<String, Object> payload = Map.of("name", "  ");

        assertThatThrownBy(() -> validator.validate(ORG, def, payload))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getViolations()).extracting("attributeCode").contains("name");
                });
    }

    @Test
    void validate_validEmbeddedComponent_passes() {
        EntityDefinitionView addressDef = new EntityDefinitionView(
                "address",
                true,
                List.of(
                        new EntityAttributeView("city", ValueKindView.TEXT, false, false, null, true)
                )
        );

        EntityDefinitionView partnerDef = new EntityDefinitionView(
                "partner",
                false,
                List.of(
                        new EntityAttributeView("name", ValueKindView.TEXT, false, false, null, true),
                        new EntityAttributeView("billingAddress", ValueKindView.REFERENCE, false, true, "address", false)
                )
        );

        when(definitionLookupPort.findByCode(ORG, "address")).thenReturn(Optional.of(addressDef));

        Map<String, Object> payload = Map.of(
                "name", "ACME",
                "billingAddress", Map.of("city", "Berlin")
        );

        validator.validate(ORG, partnerDef, payload);
    }

    @Test
    void validate_invalidEmbeddedComponent_throwsValidationException() {
        EntityDefinitionView addressDef = new EntityDefinitionView(
                "address",
                true,
                List.of(
                        new EntityAttributeView("city", ValueKindView.TEXT, false, false, null, true)
                )
        );

        EntityDefinitionView partnerDef = new EntityDefinitionView(
                "partner",
                false,
                List.of(
                        new EntityAttributeView("name", ValueKindView.TEXT, false, false, null, true),
                        new EntityAttributeView("billingAddress", ValueKindView.REFERENCE, false, true, "address", false)
                )
        );

        when(definitionLookupPort.findByCode(ORG, "address")).thenReturn(Optional.of(addressDef));

        Map<String, Object> payload = Map.of(
                "name", "ACME",
                "billingAddress", Map.of("city", "")
        );

        assertThatThrownBy(() -> validator.validate(ORG, partnerDef, payload))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getViolations()).extracting("attributeCode").contains("city");
                });
    }

    @Test
    void validate_missingEmbeddedDefinition_throwsIllegalStateException() {
        EntityDefinitionView partnerDef = new EntityDefinitionView(
                "partner",
                false,
                List.of(
                        new EntityAttributeView("billingAddress", ValueKindView.REFERENCE, false, true, "deleted_address", false)
                )
        );

        when(definitionLookupPort.findByCode(ORG, "deleted_address")).thenReturn(Optional.empty());

        Map<String, Object> payload = Map.of(
                "billingAddress", Map.of("city", "Berlin")
        );

        assertThatThrownBy(() -> validator.validate(ORG, partnerDef, payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Embedded definition 'deleted_address' referenced by 'partner.billingAddress' no longer exists");
    }

    @Test
    void validate_multiValuedEmbedded_validArray_passes() {
        EntityDefinitionView contactDef = new EntityDefinitionView(
                "contact",
                true,
                List.of(
                        new EntityAttributeView("email", ValueKindView.TEXT, false, false, null, true)
                )
        );

        EntityDefinitionView partnerDef = new EntityDefinitionView(
                "partner",
                false,
                List.of(
                        new EntityAttributeView("contacts", ValueKindView.REFERENCE, true, true, "contact", false)
                )
        );

        when(definitionLookupPort.findByCode(ORG, "contact")).thenReturn(Optional.of(contactDef));

        Map<String, Object> payload = Map.of(
                "contacts", List.of(
                        Map.of("email", "john@example.com"),
                        Map.of("email", "jane@example.com")
                )
        );

        validator.validate(ORG, partnerDef, payload);
    }

    @Test
    void validate_multiValuedEmbedded_invalidType_throwsValidationException() {
        EntityDefinitionView contactDef = new EntityDefinitionView(
                "contact",
                true,
                List.of(
                        new EntityAttributeView("email", ValueKindView.TEXT, false, false, null, true)
                )
        );

        EntityDefinitionView partnerDef = new EntityDefinitionView(
                "partner",
                false,
                List.of(
                        new EntityAttributeView("contacts", ValueKindView.REFERENCE, true, true, "contact", false)
                )
        );

        when(definitionLookupPort.findByCode(ORG, "contact")).thenReturn(Optional.of(contactDef));

        Map<String, Object> payload = Map.of(
                "contacts", "not-a-list"
        );

        assertThatThrownBy(() -> validator.validate(ORG, partnerDef, payload))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getViolations()).extracting("attributeCode").contains("contacts");
                    assertThat(ve.getViolations().get(0).message()).contains("expected an array of embedded components");
                });
    }

    @Test
    void validate_singleEmbedded_nonMapValue_isIgnoredGracefully() {
        EntityDefinitionView contactDef = new EntityDefinitionView(
                "contact",
                true,
                List.of(
                        new EntityAttributeView("email", ValueKindView.TEXT, false, false, null, true)
                )
        );

        EntityDefinitionView partnerDef = new EntityDefinitionView(
                "partner",
                false,
                List.of(
                        new EntityAttributeView("contact", ValueKindView.REFERENCE, false, true, "contact", false)
                )
        );

        when(definitionLookupPort.findByCode(ORG, "contact")).thenReturn(Optional.of(contactDef));

        Map<String, Object> payload = Map.of(
                "contact", "not-a-map"
        );

        validator.validate(ORG, partnerDef, payload);
    }

    @Test
    void validate_requiredNonStringField_passes() {
        EntityDefinitionView partnerDef = new EntityDefinitionView(
                "partner",
                false,
                List.of(
                        new EntityAttributeView("employeeCount", ValueKindView.NUMBER, false, false, null, true)
                )
        );

        Map<String, Object> payload = Map.of(
                "employeeCount", 42
        );

        validator.validate(ORG, partnerDef, payload);
    }

    @Test
    void validate_blockingRuleViolation_throwsValidationExceptionWithTheRuleMessage() {
        EntityDefinitionView def = new EntityDefinitionView("order", false, List.of(), null, "Order");
        Map<String, Object> payload = Map.of("total", 10);
        EntityRulesPort rules = (org, entityName, object) ->
                "Order".equals(entityName) ? List.of("Order total does not match the sum of line item subtotals.") : List.of();
        validator = new DefaultPayloadValidatorAdapter(definitionLookupPort, rules);

        assertThatThrownBy(() -> validator.validate(ORG, def, payload))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getViolations())
                        .containsExactly(new ValidationException.Violation(null, "Order total does not match the sum of line item subtotals.")));
    }

    @Test
    void validate_embeddedRows_areCheckedAgainstTheRulesOfTheirOwnDefinition() {
        EntityDefinitionView lineDef = new EntityDefinitionView("order-line", true, List.of(), null, "Order Line");
        EntityDefinitionView orderDef = new EntityDefinitionView("order", false, List.of(
                new EntityAttributeView("lineItems", ValueKindView.REFERENCE, true, true, "order-line", false)), null, "Order");
        when(definitionLookupPort.findByCode(ORG, "order-line")).thenReturn(Optional.of(lineDef));
        EntityRulesPort rules = (org, entityName, object) ->
                "Order Line".equals(entityName) && Integer.valueOf(0).equals(object.get("quantity")) ? List.of("Quantity must be positive.") : List.of();
        validator = new DefaultPayloadValidatorAdapter(definitionLookupPort, rules);

        Map<String, Object> payload = Map.of("lineItems", List.of(Map.of("quantity", 1), Map.of("quantity", 0)));

        assertThatThrownBy(() -> validator.validate(ORG, orderDef, payload))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getViolations()).extracting("message").containsExactly("Quantity must be positive."));
    }

    @Test
    void validate_definitionWithoutName_usesItsCodeAsRuleContext() {
        EntityDefinitionView def = new EntityDefinitionView("order", false, List.of());
        EntityRulesPort rules = (org, entityName, object) -> "order".equals(entityName) ? List.of("broken") : List.of();
        validator = new DefaultPayloadValidatorAdapter(definitionLookupPort, rules);

        assertThatThrownBy(() -> validator.validate(ORG, def, Map.of())).isInstanceOf(ValidationException.class);
    }

    private static final EntityDefinitionView NOTE_DEF = new EntityDefinitionView("note", false, List.of(
            new EntityAttributeView("comments", ValueKindView.REFERENCE, true, false, null, false, true)));

    private static Map<String, Object> comment(Object author, Object createdAt, Object content) {
        Map<String, Object> entry = new java.util.HashMap<>();
        entry.put("author", author);
        entry.put("createdAt", createdAt);
        entry.put("content", content);
        return entry;
    }

    @Test
    void validate_wellFormedComments_pass() {
        Map<String, Object> payload = Map.of("comments", List.of(
                comment("Ada", "2026-10-01T09:30:00.000Z", Map.of("type", "doc")),
                Map.of("author", "Grace", "authorId", "grace", "createdAt", "2026-10-02T10:00:00Z", "content", Map.of("type", "doc"))));

        validator.validate(ORG, NOTE_DEF, payload);
    }

    @Test
    void validate_commentsThatAreNotAnArray_throwValidationException() {
        assertThatThrownBy(() -> validator.validate(ORG, NOTE_DEF, Map.of("comments", "hello")))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getViolations())
                        .containsExactly(new ValidationException.Violation("comments", "expected an array of comments")));
    }

    @Test
    void validate_malformedCommentEntries_areEachReported() {
        Map<String, Object> payload = Map.of("comments", List.of(
                "plain text",
                comment(" ", "2026-10-01T09:30:00Z", Map.of("type", "doc")),
                comment("Ada", "yesterday", Map.of("type", "doc")),
                comment("Ada", "2026-10-01T09:30:00Z", "<p>html</p>")));

        assertThatThrownBy(() -> validator.validate(ORG, NOTE_DEF, payload))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getViolations()).extracting("message").containsExactly(
                        "comment 0 is not an object",
                        "comment 1 needs an author",
                        "comment 2 needs an ISO-8601 createdAt",
                        "comment 3 needs rich text content"));
    }

    private static final EntityDefinitionView CUSTOMER_DEF = new EntityDefinitionView("customer", false, List.of(
            new EntityAttributeView("address", ValueKindView.REFERENCE, false, false, null, false, false, true)));

    @Test
    void validate_wellFormedAddresses_pass() {
        validator.validate(ORG, CUSTOMER_DEF, Map.of("address", Map.of("country", "HU", "city", "Budapest", "street", "Fő utca 1.")));
        validator.validate(ORG, CUSTOMER_DEF, Map.of("address", Map.of("city", "Budapest", "latitude", 47.5, "longitude", 19, "locationSource", "MANUAL")));
        validator.validate(ORG, CUSTOMER_DEF, Map.of("address", Map.of()));
    }

    @Test
    void validate_addressThatIsNotAnObject_throwsValidationException() {
        Map<String, Object> payload = Map.of("address", "Fő utca 1.");

        assertThatThrownBy(() -> validator.validate(ORG, CUSTOMER_DEF, payload))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getViolations())
                        .containsExactly(new ValidationException.Violation("address", "expected an address object")));
    }

    @Test
    void validate_malformedAddressParts_areEachReported() {
        Map<String, Object> address = new java.util.HashMap<>();
        address.put("city", 42);
        address.put("country", "Hungary");
        address.put("latitude", 47.5);
        address.put("locationSource", "GUESSED");
        Map<String, Object> payload = Map.of("address", address);

        assertThatThrownBy(() -> validator.validate(ORG, CUSTOMER_DEF, payload))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getViolations()).extracting("message").containsExactly(
                        "address city must be text",
                        "address country must be an ISO 3166-1 alpha-2 code",
                        "address latitude and longitude go together",
                        "address locationSource must be GEOCODED or MANUAL"));
    }

    @Test
    void validate_addressCoordinatesOutOfRange_throwValidationException() {
        for (Map<String, Object> address : List.of(Map.<String, Object>of("latitude", 91, "longitude", 0), Map.<String, Object>of("latitude", 0, "longitude", -181),
                Map.<String, Object>of("latitude", "47.5", "longitude", 19))) {
            Map<String, Object> payload = Map.of("address", address);
            assertThatThrownBy(() -> validator.validate(ORG, CUSTOMER_DEF, payload))
                    .as("address %s", address)
                    .isInstanceOf(ValidationException.class)
                    .satisfies(ex -> assertThat(((ValidationException) ex).getViolations()).extracting("message")
                            .containsExactly("address latitude must be within 90 and longitude within 180 degrees"));
        }
    }

    private static final EntityDefinitionView VISIT_DEF = new EntityDefinitionView("visit", false, List.of(
            new EntityAttributeView("location", ValueKindView.REFERENCE, false, false, null, false, false, false, true)));

    @Test
    void validate_wellFormedGeoLocations_pass() {
        validator.validate(ORG, VISIT_DEF, Map.of("location", Map.of("latitude", 47.5, "longitude", 19, "accuracy", 12.5, "capturedAt", "2026-10-17T08:00:00Z")));
        validator.validate(ORG, VISIT_DEF, Map.of("location", Map.of("latitude", -33.9, "longitude", 151.2)));
    }

    @Test
    void validate_geoLocationThatIsNotAnObject_throwsValidationException() {
        Map<String, Object> payload = Map.of("location", "47.5, 19");

        assertThatThrownBy(() -> validator.validate(ORG, VISIT_DEF, payload))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getViolations())
                        .containsExactly(new ValidationException.Violation("location", "expected a geolocation object")));
    }

    @Test
    void validate_malformedGeoLocationParts_areEachReported() {
        Map<String, Object> payload = Map.of("location", Map.of("latitude", 91, "accuracy", -1, "capturedAt", "yesterday"));

        assertThatThrownBy(() -> validator.validate(ORG, VISIT_DEF, payload))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getViolations()).extracting("message").containsExactly(
                        "geolocation needs a latitude within 90 and a longitude within 180 degrees",
                        "geolocation accuracy must be a non-negative number of metres",
                        "geolocation capturedAt must be an ISO 8601 instant"));
    }
}
