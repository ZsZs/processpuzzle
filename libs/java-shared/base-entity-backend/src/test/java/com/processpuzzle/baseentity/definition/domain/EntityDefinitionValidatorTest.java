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
    void validate_dateFormatOnDateKinds_passes() {
        validator.validate(definitionWith(dateAttribute(ValueKind.DATE, new DateFormat(DateStyle.SHORT, TimeStyle.NONE))));
        validator.validate(definitionWith(dateAttribute(ValueKind.DATE_TIME, new DateFormat(DateStyle.MEDIUM, TimeStyle.SHORT))));
        validator.validate(definitionWith(dateAttribute(ValueKind.DATE_TIME, new DateFormat(DateStyle.NONE, TimeStyle.SHORT))));
    }

    @Test
    void validate_dateFormatOnNonDateKind_throwsValidationException() {
        assertSingleViolation(dateAttribute(ValueKind.TEXT, new DateFormat(DateStyle.SHORT, null)),
                "dateFormat requires valueKind=DATE or DATE_TIME");
    }

    @Test
    void validate_dateFormatTimeStyleOnDate_throwsValidationException() {
        assertSingleViolation(dateAttribute(ValueKind.DATE, new DateFormat(DateStyle.SHORT, TimeStyle.SHORT)),
                "valueKind=DATE cannot declare a dateFormat timeStyle");
    }

    @Test
    void validate_dateFormatBothNone_throwsValidationException() {
        assertSingleViolation(dateAttribute(ValueKind.DATE_TIME, new DateFormat(DateStyle.NONE, TimeStyle.NONE)),
                "dateStyle and timeStyle cannot both be none");
    }

    @Test
    void validate_multiplicityWithConsistentMaxOccurs_passes() {
        validator.validate(definitionWith(multiplicityAttribute(null, null)));
        validator.validate(definitionWith(multiplicityAttribute(Multiplicity.ZERO_TO_ONE, null)));
        validator.validate(definitionWith(multiplicityAttribute(Multiplicity.ONE_TO_N, null)));
        validator.validate(definitionWith(multiplicityAttribute(Multiplicity.ZERO_TO_X, 1)));
        validator.validate(definitionWith(multiplicityAttribute(Multiplicity.ONE_TO_X, 5)));
    }

    @Test
    void validate_boundedMultiplicityWithoutMaxOccurs_throwsValidationException() {
        assertSingleViolation(multiplicityAttribute(Multiplicity.ZERO_TO_X, null),
                "multiplicity 0..x requires maxOccurs of at least 1");
        assertSingleViolation(multiplicityAttribute(Multiplicity.ONE_TO_X, 0),
                "multiplicity 1..x requires maxOccurs of at least 1");
    }

    @Test
    void validate_maxOccursOnUnboundedMultiplicity_throwsValidationException() {
        assertSingleViolation(multiplicityAttribute(Multiplicity.ZERO_TO_N, 3),
                "maxOccurs is only meaningful for multiplicity 0..x or 1..x");
        assertSingleViolation(multiplicityAttribute(null, 3),
                "maxOccurs is only meaningful for multiplicity 0..x or 1..x");
    }

    @Test
    void multiplicity_upperBoundAndMultiValued() {
        assertThat(Multiplicity.ZERO_TO_ONE.upperBound(null)).isEqualTo(1);
        assertThat(Multiplicity.ZERO_TO_X.upperBound(4)).isEqualTo(4);
        assertThat(Multiplicity.ONE_TO_X.upperBound(null)).isEqualTo(1);
        assertThat(Multiplicity.ZERO_TO_N.upperBound(null)).isEqualTo(Integer.MAX_VALUE);
        assertThat(Multiplicity.ONE_TO_X.isMultiValued(1)).isFalse();
        assertThat(Multiplicity.ONE_TO_X.isMultiValued(2)).isTrue();
        assertThat(Multiplicity.isMultiValued(null, null)).isFalse();
        assertThat(Multiplicity.isMultiValued(Multiplicity.ONE_TO_N, null)).isTrue();
        assertThat(Multiplicity.ZERO_TO_N.notation()).isEqualTo("0..n");
    }

    @Test
    void validate_commentsAsMultiValuedReference_passes() {
        validator.validate(definitionWith(commentsAttribute(ValueKind.REFERENCE, Multiplicity.ZERO_TO_N, null)));
    }

    @Test
    void validate_commentsOfAnotherShape_throwsValidationException() {
        String message = "COMMENTS attributes must declare valueKind=REFERENCE, a multi-valued multiplicity and no linkedEntityType";
        assertSingleViolation(commentsAttribute(ValueKind.TEXT, Multiplicity.ZERO_TO_N, null), message);
        assertSingleViolation(commentsAttribute(ValueKind.REFERENCE, Multiplicity.ZERO_TO_ONE, null), message);
        assertSingleViolation(commentsAttribute(ValueKind.REFERENCE, null, null), message);
        assertSingleViolation(commentsAttribute(ValueKind.REFERENCE, Multiplicity.ZERO_TO_N, "note"), message);
    }

    private static BaseEntityAttribute commentsAttribute(ValueKind valueKind, Multiplicity multiplicity, String linkedEntityType) {
        return BaseEntityAttribute.builder()
                .code("comments")
                .name("Comments")
                .valueKind(valueKind)
                .formControlType(FormControlType.COMMENTS)
                .multiplicity(multiplicity)
                .linkedEntityType(linkedEntityType)
                .build();
    }

    private static BaseEntityAttribute multiplicityAttribute(Multiplicity multiplicity, Integer maxOccurs) {
        return BaseEntityAttribute.builder()
                .code("attachments")
                .name("Attachments")
                .valueKind(ValueKind.REFERENCE)
                .formControlType(FormControlType.ARTIFACT)
                .multiplicity(multiplicity)
                .maxOccurs(maxOccurs)
                .build();
    }

    private static BaseEntityAttribute dateAttribute(ValueKind valueKind, DateFormat dateFormat) {
        return BaseEntityAttribute.builder()
                .code("born")
                .name("Born")
                .valueKind(valueKind)
                .formControlType(FormControlType.DATE)
                .dateFormat(dateFormat)
                .build();
    }

    private static BaseEntityDefinition definitionWith(BaseEntityAttribute attribute) {
        return BaseEntityDefinition.builder().code("person").name("Person").attributes(List.of(attribute)).build();
    }

    private void assertSingleViolation(BaseEntityAttribute attribute, String message) {
        assertThatThrownBy(() -> validator.validate(definitionWith(attribute)))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getViolations()).hasSize(1);
                    assertThat(ve.getViolations().get(0).message()).contains(message);
                });
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

    @Test
    void validate_addressAsSingleReferenceWithMapOptions_passes() {
        BaseEntityAttribute address = addressAttribute(ValueKind.REFERENCE, null, null);
        address.setShowMap(true);
        address.setMapProvider(MapProvider.GOOGLE);
        address.setDefaultCountry("HU");
        validator.validate(definitionWith(address));
    }

    @Test
    void validate_addressOfAnotherShape_throwsValidationException() {
        String message = "ADDRESS attributes must declare valueKind=REFERENCE, a single value and no linkedEntityType";
        assertSingleViolation(addressAttribute(ValueKind.TEXT, null, null), message);
        assertSingleViolation(addressAttribute(ValueKind.REFERENCE, Multiplicity.ZERO_TO_N, null), message);
        assertSingleViolation(addressAttribute(ValueKind.REFERENCE, null, "location"), message);
    }

    @Test
    void validate_addressDefaultCountryThatIsNotAnIsoCode_throwsValidationException() {
        BaseEntityAttribute address = addressAttribute(ValueKind.REFERENCE, null, null);
        address.setDefaultCountry("Hungary");
        assertSingleViolation(address, "defaultCountry must be an ISO 3166-1 alpha-2 code, e.g. HU");
    }

    @Test
    void validate_mapOptionsOnAnotherControl_throwValidationException() {
        String message = "showMap, mapProvider and defaultCountry are only meaningful for ADDRESS attributes";
        BaseEntityAttribute withMap = textAttribute();
        withMap.setShowMap(true);
        assertSingleViolation(withMap, message);
        BaseEntityAttribute withProvider = textAttribute();
        withProvider.setMapProvider(MapProvider.OPENSTREETMAP);
        assertSingleViolation(withProvider, message);
        BaseEntityAttribute withCountry = textAttribute();
        withCountry.setDefaultCountry("DE");
        assertSingleViolation(withCountry, message);
    }

    @Test
    void validate_geoLocationAsSingleReference_passes() {
        validator.validate(definitionWith(geoLocationAttribute(ValueKind.REFERENCE, null, null)));
    }

    @Test
    void validate_geoLocationOfAnotherShape_throwsValidationException() {
        String message = "GEO_LOCATION attributes must declare valueKind=REFERENCE, a single value and no linkedEntityType";
        assertSingleViolation(geoLocationAttribute(ValueKind.TEXT, null, null), message);
        assertSingleViolation(geoLocationAttribute(ValueKind.REFERENCE, Multiplicity.ZERO_TO_N, null), message);
        assertSingleViolation(geoLocationAttribute(ValueKind.REFERENCE, null, "place"), message);
    }

    private static BaseEntityAttribute geoLocationAttribute(ValueKind valueKind, Multiplicity multiplicity, String linkedEntityType) {
        return BaseEntityAttribute.builder()
                .code("location")
                .name("Location")
                .valueKind(valueKind)
                .formControlType(FormControlType.GEO_LOCATION)
                .multiplicity(multiplicity)
                .linkedEntityType(linkedEntityType)
                .build();
    }

    private static BaseEntityAttribute addressAttribute(ValueKind valueKind, Multiplicity multiplicity, String linkedEntityType) {
        return BaseEntityAttribute.builder()
                .code("address")
                .name("Address")
                .valueKind(valueKind)
                .formControlType(FormControlType.ADDRESS)
                .multiplicity(multiplicity)
                .linkedEntityType(linkedEntityType)
                .build();
    }

    private static BaseEntityAttribute textAttribute() {
        return BaseEntityAttribute.builder().code("street").name("Street").valueKind(ValueKind.TEXT).formControlType(FormControlType.TEXT_BOX).build();
    }
}
