package com.processpuzzle.baseentity.instances.adapters.outbound;

import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView.ValueKindView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
}
