package com.processpuzzle.baseentity.definition.domain;

import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.core.quantity.UnitCatalog;
import com.processpuzzle.core.quantity.UnitCatalogProvider;
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
    void validate_standaloneContextObject_passes() {
        validator.validate(BaseEntityDefinition.builder().code("race").name("Race").canContextObject(true).build());
    }

    @Test
    void validate_embeddedContextObject_throwsValidationException() {
        BaseEntityDefinition definition = BaseEntityDefinition.builder()
                .code("line").name("Line").isEmbedded(true).componentParents(List.of("order")).canContextObject(true).build();

        assertThatThrownBy(() -> validator.validate(definition))
                .isInstanceOfSatisfying(ValidationException.class, ve ->
                        assertThat(ve.getViolations().get(0).message()).contains("canContextObject"));
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
    void validate_snapshotOnAReference_passes() {
        validator.validate(definitionWith(snapshotAttribute(ValueKind.REFERENCE, "boat", List.of("sailNumber", "name"))));
    }

    @Test
    void validate_snapshotOnANonReference_throwsValidationException() {
        assertSingleViolation(snapshotAttribute(ValueKind.TEXT, "boat", List.of("sailNumber")),
                "snapshotFields need a REFERENCE attribute with a linkedEntityType");
    }

    @Test
    void validate_snapshotWithoutLinkedType_throwsValidationException() {
        BaseEntityAttribute avatar = BaseEntityAttribute.builder()
                .code("boat")
                .name("Boat")
                .valueKind(ValueKind.REFERENCE)
                .formControlType(FormControlType.AVATAR)
                .snapshotFields(List.of("sailNumber"))
                .build();
        assertSingleViolation(avatar, "snapshotFields need a REFERENCE attribute with a linkedEntityType");
    }

    @Test
    void validate_snapshotNamingAFieldTwice_throwsValidationException() {
        assertSingleViolation(snapshotAttribute(ValueKind.REFERENCE, "boat", List.of("sailNumber", "sailNumber")),
                "snapshotFields must not name an attribute twice");
    }

    @Test
    void validate_snapshotNamingABlankField_throwsValidationException() {
        assertSingleViolation(snapshotAttribute(ValueKind.REFERENCE, "boat", List.of(" ")),
                "snapshotFields must not name a blank attribute");
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

    private static BaseEntityAttribute snapshotAttribute(ValueKind valueKind, String linkedEntityType, List<String> fields) {
        return BaseEntityAttribute.builder()
                .code("boat")
                .name("Boat")
                .valueKind(valueKind)
                .formControlType(FormControlType.FOREIGN_KEY)
                .linkedEntityType(linkedEntityType)
                .snapshotFields(fields)
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

    @Test
    void validate_calculatedOfAnotherShape_throwsValidationException() {
        String message = "CALCULATED attributes must declare valueKind NUMBER, TEXT, DATE or BOOLEAN, a single value and no linkedEntityType";
        assertSingleViolation(calculatedAttribute(ValueKind.REFERENCE, null, null), message);
        assertSingleViolation(calculatedAttribute(ValueKind.NUMBER, Multiplicity.ZERO_TO_N, null), message);
        assertSingleViolation(calculatedAttribute(ValueKind.NUMBER, null, "place"), message);
    }

    @Test
    void validate_calculatedScalar_passes() {
        for (ValueKind kind : List.of(ValueKind.NUMBER, ValueKind.TEXT, ValueKind.DATE, ValueKind.BOOLEAN)) {
            validator.validate(definitionWith(calculatedAttribute(kind, null, null)));
        }
    }

    private static BaseEntityAttribute calculatedAttribute(ValueKind valueKind, Multiplicity multiplicity, String linkedEntityType) {
        return BaseEntityAttribute.builder().code("total").name("Total").valueKind(valueKind)
                .formControlType(FormControlType.CALCULATED).multiplicity(multiplicity).linkedEntityType(linkedEntityType).build();
    }

    @Test
    void validate_measureAttributesWithTheirOwnOptions_pass() {
        BaseEntityAttribute quantity = measureAttribute(FormControlType.QUANTITY);
        quantity.setQuantityKind("MASS");
        quantity.setAllowedUnits(List.of("kg", "g"));
        quantity.setDefaultUnit("g");
        quantity.setScale(3);
        validator.validate(definitionWith(quantity));

        BaseEntityAttribute money = measureAttribute(FormControlType.MONEY);
        money.setAllowedCurrencies(List.of("EUR", "HUF"));
        money.setDefaultCurrency("HUF");
        validator.validate(definitionWith(money));

        BaseEntityAttribute dimensions = measureAttribute(FormControlType.DIMENSIONS);
        dimensions.setAllowedUnits(List.of("cm", "m"));
        dimensions.setDefaultUnit("cm");
        dimensions.setScale(1);
        dimensions.setDimensionCount(3);
        validator.validate(definitionWith(dimensions));

        validator.validate(definitionWith(measureAttribute(FormControlType.QUANTITY)));
    }

    @Test
    void validate_measureOfAnotherShape_throwsValidationException() {
        for (FormControlType type : List.of(FormControlType.QUANTITY, FormControlType.MONEY, FormControlType.DIMENSIONS)) {
            String message = type + " attributes must declare valueKind=REFERENCE, a single value and no linkedEntityType";
            BaseEntityAttribute text = measureAttribute(type);
            text.setValueKind(ValueKind.TEXT);
            assertSingleViolation(text, message);
            BaseEntityAttribute many = measureAttribute(type);
            many.setMultiplicity(Multiplicity.ZERO_TO_N);
            assertSingleViolation(many, message);
            BaseEntityAttribute linked = measureAttribute(type);
            linked.setLinkedEntityType("unit");
            assertSingleViolation(linked, message);
        }
    }

    @Test
    void validate_quantityNamingAnOrganizationUnit_isReadInThatOrganizationsCatalog() {
        UnitCatalog withPallet = UnitCatalog.standard().withUnits(List.of(
                new UnitCatalog.ExtraUnit("MASS", "pallet", "plt", "Pallet", new java.math.BigDecimal("500"), java.math.BigDecimal.ONE)));
        UnitCatalogProvider catalogs = orgKey -> "acme".equals(orgKey) ? withPallet : UnitCatalog.standard();
        EntityDefinitionValidator orgAware = new EntityDefinitionValidator(catalogs);
        BaseEntityAttribute load = measureAttribute(FormControlType.QUANTITY);
        load.setAllowedUnits(List.of("kg", "pallet"));
        BaseEntityDefinition definition = definitionWith(load);

        orgAware.validate("acme", definition);

        assertThatThrownBy(() -> orgAware.validate("other", definition)).isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getViolations()).extracting(ValidationException.Violation::message)
                        .contains("allowedUnits names unknown unit 'pallet'"));
        definition.setOrgKey("acme");
        orgAware.validate(definition);
        assertThatThrownBy(() -> validator.validate("acme", definition)).as("a validator without organization units").isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_quantityNamingWhatTheCatalogDoesNotKnow_throwsValidationException() {
        BaseEntityAttribute kind = measureAttribute(FormControlType.QUANTITY);
        kind.setQuantityKind("LUMINOSITY");
        assertSingleViolation(kind, "quantityKind 'LUMINOSITY' is not a quantity kind of the catalog");

        BaseEntityAttribute unit = measureAttribute(FormControlType.QUANTITY);
        unit.setAllowedUnits(List.of("kg", "parsec"));
        assertSingleViolation(unit, "allowedUnits names unknown unit 'parsec'");

        BaseEntityAttribute unitOfAnotherKind = measureAttribute(FormControlType.QUANTITY);
        unitOfAnotherKind.setQuantityKind("MASS");
        unitOfAnotherKind.setAllowedUnits(List.of("kg", "m"));
        assertSingleViolation(unitOfAnotherKind, "allowedUnits 'm' measures LENGTH, not MASS");

        BaseEntityAttribute defaultOfAnotherKind = measureAttribute(FormControlType.QUANTITY);
        defaultOfAnotherKind.setQuantityKind("MASS");
        defaultOfAnotherKind.setDefaultUnit("m");
        assertSingleViolation(defaultOfAnotherKind, "defaultUnit 'm' measures LENGTH, not MASS");

        BaseEntityAttribute unknownDefault = measureAttribute(FormControlType.QUANTITY);
        unknownDefault.setDefaultUnit("parsec");
        assertSingleViolation(unknownDefault, "defaultUnit names unknown unit 'parsec'");
    }

    @Test
    void validate_measureWithABlankLinkedTypeIsAsGoodAsOneWithout() {
        BaseEntityAttribute quantity = measureAttribute(FormControlType.QUANTITY);
        quantity.setLinkedEntityType(" ");
        validator.validate(definitionWith(quantity));
    }

    @Test
    void validate_defaultUnitOutsideTheAllowedOnes_throwsValidationException() {
        BaseEntityAttribute quantity = measureAttribute(FormControlType.QUANTITY);
        quantity.setAllowedUnits(List.of("kg", "g"));
        quantity.setDefaultUnit("t");
        assertSingleViolation(quantity, "defaultUnit 't' is not one of the allowedUnits");
    }

    @Test
    void validate_scaleOutsideZeroToTwenty_throwsValidationException() {
        for (int scale : new int[] {-1, 21}) {
            BaseEntityAttribute quantity = measureAttribute(FormControlType.QUANTITY);
            quantity.setScale(scale);
            assertSingleViolation(quantity, "scale must be between 0 and 20");
        }
        BaseEntityAttribute edge = measureAttribute(FormControlType.QUANTITY);
        edge.setScale(0);
        validator.validate(definitionWith(edge));
        edge.setScale(20);
        validator.validate(definitionWith(edge));
    }

    @Test
    void validate_dimensionsOfAnotherKindOrCount_throwsValidationException() {
        BaseEntityAttribute mass = measureAttribute(FormControlType.DIMENSIONS);
        mass.setAllowedUnits(List.of("cm", "kg"));
        assertSingleViolation(mass, "allowedUnits 'kg' measures MASS, not LENGTH");

        BaseEntityAttribute defaultMass = measureAttribute(FormControlType.DIMENSIONS);
        defaultMass.setDefaultUnit("kg");
        assertSingleViolation(defaultMass, "defaultUnit 'kg' measures MASS, not LENGTH");

        BaseEntityAttribute four = measureAttribute(FormControlType.DIMENSIONS);
        four.setDimensionCount(4);
        assertSingleViolation(four, "dimensionCount must be 2 or 3");

        BaseEntityAttribute two = measureAttribute(FormControlType.DIMENSIONS);
        two.setDimensionCount(2);
        validator.validate(definitionWith(two));
    }

    @Test
    void validate_moneyNamingWhatTheCatalogDoesNotKnow_throwsValidationException() {
        BaseEntityAttribute unknown = measureAttribute(FormControlType.MONEY);
        unknown.setAllowedCurrencies(List.of("EUR", "XXX"));
        assertSingleViolation(unknown, "allowedCurrencies names unknown currency 'XXX'");

        BaseEntityAttribute unknownDefault = measureAttribute(FormControlType.MONEY);
        unknownDefault.setAllowedCurrencies(List.of("EUR"));
        unknownDefault.setDefaultCurrency("XXX");
        assertSingleViolation(unknownDefault, "defaultCurrency names unknown currency 'XXX'");

        BaseEntityAttribute outside = measureAttribute(FormControlType.MONEY);
        outside.setAllowedCurrencies(List.of("EUR"));
        outside.setDefaultCurrency("USD");
        assertSingleViolation(outside, "defaultCurrency 'USD' is not one of the allowedCurrencies");

        BaseEntityAttribute anyAllowed = measureAttribute(FormControlType.MONEY);
        anyAllowed.setDefaultCurrency("USD");
        validator.validate(definitionWith(anyAllowed));
    }

    @Test
    void validate_optionsAnotherControlOwns_throwsValidationException() {
        BaseEntityAttribute money = measureAttribute(FormControlType.MONEY);
        money.setAllowedUnits(List.of("kg"));
        money.setScale(2);
        assertSingleViolation(money, "MONEY attributes cannot set allowedUnits, scale");

        BaseEntityAttribute quantity = measureAttribute(FormControlType.QUANTITY);
        quantity.setDefaultCurrency("EUR");
        quantity.setDimensionCount(3);
        assertSingleViolation(quantity, "QUANTITY attributes cannot set defaultCurrency, dimensionCount");

        BaseEntityAttribute dimensions = measureAttribute(FormControlType.DIMENSIONS);
        dimensions.setQuantityKind("LENGTH");
        dimensions.setAllowedCurrencies(List.of("EUR"));
        assertSingleViolation(dimensions, "DIMENSIONS attributes cannot set quantityKind, allowedCurrencies");
    }

    @Test
    void validate_measureOptionsOnAnotherControl_throwsValidationException() {
        BaseEntityAttribute text = textAttribute();
        text.setScale(2);
        text.setAllowedUnits(List.of("kg"));
        assertSingleViolation(text, "allowedUnits, scale only mean something on QUANTITY, MONEY and DIMENSIONS attributes");

        BaseEntityAttribute emptyLists = textAttribute();
        emptyLists.setAllowedUnits(List.of());
        emptyLists.setAllowedCurrencies(List.of());
        validator.validate(definitionWith(emptyLists));
    }

    private static BaseEntityAttribute measureAttribute(FormControlType type) {
        return BaseEntityAttribute.builder()
                .code("measure")
                .name("Measure")
                .valueKind(ValueKind.REFERENCE)
                .formControlType(type)
                .build();
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

    private static BaseEntityDefinition withIdentifiers(List<List<String>> identifiers) {
        BaseEntityAttribute text = BaseEntityAttribute.builder().code("name").name("Name")
                .valueKind(ValueKind.TEXT).formControlType(FormControlType.TEXT).build();
        return BaseEntityDefinition.builder().code("club").name("Club").attributes(List.of(text)).identifiers(identifiers).build();
    }

    @Test
    void validate_identifiersNamingTheTypesAttributes_pass() {
        validator.validate(withIdentifiers(List.of(List.of("name"))));
    }

    @Test
    void validate_identifierNamingAnUnknownAttribute_isRefused() {
        assertThatThrownBy(() -> validator.validate(withIdentifiers(List.of(List.of("name", "country")))))
                .isInstanceOf(com.processpuzzle.baseentity.common.ValidationException.class);
    }

    @Test
    void validate_emptyOrRepeatedIdentifier_isRefused() {
        assertThatThrownBy(() -> validator.validate(withIdentifiers(List.of(List.of()))))
                .isInstanceOf(com.processpuzzle.baseentity.common.ValidationException.class);
        assertThatThrownBy(() -> validator.validate(withIdentifiers(List.of(List.of("name"), List.of("name")))))
                .isInstanceOf(com.processpuzzle.baseentity.common.ValidationException.class);
    }

    private static BaseEntityDefinition withLayout(List<List<String>> layout) {
        List<BaseEntityAttribute> attributes = List.of("name", "category", "state").stream()
                .map(code -> BaseEntityAttribute.builder().code(code).name(code)
                        .valueKind(ValueKind.TEXT).formControlType(FormControlType.TEXT).build())
                .toList();
        return BaseEntityDefinition.builder().code("area").name("Area").attributes(attributes).layout(layout).build();
    }

    @Test
    void validate_layoutNamingTheTypesAttributesOnce_passes() {
        validator.validate(withLayout(List.of(List.of("name", "category"), List.of("state"))));
        validator.validate(withLayout(List.of(List.of("name"))));
        validator.validate(withLayout(List.of()));
    }

    @Test
    void validate_layoutWithAnEmptyRow_isRefused() {
        assertThatThrownBy(() -> validator.validate(withLayout(List.of(List.of("name"), List.of()))))
                .isInstanceOf(com.processpuzzle.baseentity.common.ValidationException.class);
    }

    @Test
    void validate_layoutNamingAnUnknownAttribute_isRefused() {
        assertThatThrownBy(() -> validator.validate(withLayout(List.of(List.of("name", "country")))))
                .isInstanceOf(com.processpuzzle.baseentity.common.ValidationException.class);
    }

    @Test
    void validate_layoutPlacingAnAttributeTwice_isRefused() {
        assertThatThrownBy(() -> validator.validate(withLayout(List.of(List.of("name"), List.of("state", "name")))))
                .isInstanceOf(com.processpuzzle.baseentity.common.ValidationException.class);
    }
}
