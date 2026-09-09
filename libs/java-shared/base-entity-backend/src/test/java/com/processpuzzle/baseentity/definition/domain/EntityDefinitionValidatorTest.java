package com.processpuzzle.baseentity.definition.domain;

import com.processpuzzle.baseentity.common.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityDefinitionValidatorTest {

    private EntityDefinitionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new EntityDefinitionValidator();
    }

    @Test
    void validate_validStandaloneDefinition_passes() {
        BaseEntityDefinition definition = BaseEntityDefinition.builder()
                .code("partner")
                .name("Partner")
                .isEmbedded(false)
                .attributes(List.of(
                        BaseEntityAttribute.builder()
                                .code("name")
                                .name("Name")
                                .valueKind(ValueKind.TEXT)
                                .formControlType(FormControlType.TEXT)
                                .build()
                ))
                .build();

        validator.validate(definition);
    }

    @Test
    void validate_embeddedWithoutComponentParents_throwsValidationException() {
        BaseEntityDefinition definition = BaseEntityDefinition.builder()
                .code("address")
                .name("Address")
                .isEmbedded(true)
                .componentParents(List.of())
                .attributes(List.of())
                .build();

        assertThatThrownBy(() -> validator.validate(definition))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getViolations()).hasSize(1);
                    assertThat(ve.getViolations().get(0).message()).contains("declares isEmbedded without a componentParent");
                });
    }

    @Test
    void validate_enumSelectWithoutEnumKind_throwsValidationException() {
        BaseEntityDefinition definition = BaseEntityDefinition.builder()
                .code("user")
                .name("User")
                .attributes(List.of(
                        BaseEntityAttribute.builder()
                                .code("role")
                                .name("Role")
                                .valueKind(ValueKind.TEXT)
                                .formControlType(FormControlType.ENUM_SELECT)
                                .build()
                ))
                .build();

        assertThatThrownBy(() -> validator.validate(definition))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getViolations()).extracting("attributeCode")
                            .contains("role");
                });
    }

    @Test
    void validate_enumKindWithoutEnumValues_throwsValidationException() {
        BaseEntityDefinition definition = BaseEntityDefinition.builder()
                .code("ticket")
                .name("Ticket")
                .attributes(List.of(
                        BaseEntityAttribute.builder()
                                .code("priority")
                                .name("Priority")
                                .valueKind(ValueKind.ENUM)
                                .formControlType(FormControlType.ENUM_SELECT)
                                .enumValues(List.of())
                                .build()
                ))
                .build();

        assertThatThrownBy(() -> validator.validate(definition))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getViolations()).extracting("attributeCode")
                            .contains("priority");
                });
    }

    @Test
    void validate_foreignKeyOrComponentWithoutLinkedEntityType_throwsValidationException() {
        BaseEntityDefinition definition = BaseEntityDefinition.builder()
                .code("order")
                .name("Order")
                .attributes(List.of(
                        BaseEntityAttribute.builder()
                                .code("customer")
                                .name("Customer")
                                .valueKind(ValueKind.REFERENCE)
                                .formControlType(FormControlType.FOREIGN_KEY)
                                .linkedEntityType(null)
                                .build()
                ))
                .build();

        assertThatThrownBy(() -> validator.validate(definition))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getViolations()).extracting("attributeCode")
                            .contains("customer");
                });
    }

    @Test
    void validate_duplicateEmbeddedComponents_throwsValidationException() {
        BaseEntityDefinition definition = BaseEntityDefinition.builder()
                .code("order")
                .name("Order")
                .attributes(List.of(
                        BaseEntityAttribute.builder()
                                .code("shippingAddress")
                                .name("Shipping Address")
                                .valueKind(ValueKind.REFERENCE)
                                .formControlType(FormControlType.EMBEDDED_COMPONENTS)
                                .linkedEntityType("address")
                                .build(),
                        BaseEntityAttribute.builder()
                                .code("billingAddress")
                                .name("Billing Address")
                                .valueKind(ValueKind.REFERENCE)
                                .formControlType(FormControlType.EMBEDDED_COMPONENTS)
                                .linkedEntityType("address")
                                .build()
                ))
                .build();

        assertThatThrownBy(() -> validator.validate(definition))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getViolations()).hasSize(1);
                    assertThat(ve.getViolations().get(0).message()).contains("declares 2 attributes as embedded 'address' components");
                });
    }
}
