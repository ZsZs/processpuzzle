package com.processpuzzle.baseentity.instances.adapters.outbound;

import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView.ValueKindView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
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

    @Mock
    private EntityDefinitionLookupPort definitionLookupPort;

    private DefaultPayloadValidatorAdapter validator;

    @BeforeEach
    void setUp() {
        validator = new DefaultPayloadValidatorAdapter(definitionLookupPort);
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

        validator.validate(def, payload);
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

        assertThatThrownBy(() -> validator.validate(def, payload))
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

        when(definitionLookupPort.findByCode("address")).thenReturn(Optional.of(addressDef));

        Map<String, Object> payload = Map.of(
                "name", "ACME",
                "billingAddress", Map.of("city", "Berlin")
        );

        validator.validate(partnerDef, payload);
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

        when(definitionLookupPort.findByCode("address")).thenReturn(Optional.of(addressDef));

        Map<String, Object> payload = Map.of(
                "name", "ACME",
                "billingAddress", Map.of("city", "")
        );

        assertThatThrownBy(() -> validator.validate(partnerDef, payload))
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

        when(definitionLookupPort.findByCode("deleted_address")).thenReturn(Optional.empty());

        Map<String, Object> payload = Map.of(
                "billingAddress", Map.of("city", "Berlin")
        );

        assertThatThrownBy(() -> validator.validate(partnerDef, payload))
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

        when(definitionLookupPort.findByCode("contact")).thenReturn(Optional.of(contactDef));

        Map<String, Object> payload = Map.of(
                "contacts", List.of(
                        Map.of("email", "john@example.com"),
                        Map.of("email", "jane@example.com")
                )
        );

        validator.validate(partnerDef, payload);
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

        when(definitionLookupPort.findByCode("contact")).thenReturn(Optional.of(contactDef));

        Map<String, Object> payload = Map.of(
                "contacts", "not-a-list"
        );

        assertThatThrownBy(() -> validator.validate(partnerDef, payload))
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

        when(definitionLookupPort.findByCode("contact")).thenReturn(Optional.of(contactDef));

        Map<String, Object> payload = Map.of(
                "contact", "not-a-map"
        );

        validator.validate(partnerDef, payload);
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

        validator.validate(partnerDef, payload);
    }
}
