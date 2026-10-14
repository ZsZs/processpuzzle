package com.processpuzzle.baseentity.definition.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A release copies a draft's attributes onto the released definition through {@link BaseEntityAttribute#copyFrom}.
 * A field added to the attribute and forgotten there would be silently dropped from every release; this fails
 * instead.
 */
class BaseEntityAttributeCopyTest {

    /** Assigned, not authored: a released attribute keeps its own. */
    private static final Set<String> NOT_COPIED = Set.of("id", "entityDefinition");

    @Test
    void copiesEveryAuthoredField() throws IllegalAccessException {
        BaseEntityAttribute source = BaseEntityAttribute.builder()
            .id(UUID.randomUUID())
            .code("amount")
            .name("Amount")
            .displayOrder(7)
            .valueKind(ValueKind.NUMBER)
            .formControlType(FormControlType.TEXT_BOX)
            .multiplicity(Multiplicity.ZERO_TO_X)
            .maxOccurs(3)
            .required(true)
            .indexed(true)
            .defaultValue(42)
            .enumValues(List.of("a", "b"))
            .dateFormat(new DateFormat(DateStyle.SHORT, TimeStyle.SHORT))
            .linkedEntityType("currency")
            .isLinkToDetails(true)
            .defaultAvatar("person")
            .autosizeColumn(true)
            .validationRules(Map.of("min", 0))
            .build();

        BaseEntityAttribute copy = source.copy();

        for (Field field : authoredFields()) {
            field.setAccessible(true);
            assertThat(field.get(copy)).as("field '%s' is not copied", field.getName()).isEqualTo(field.get(source));
            assertThat(field.get(source)).as("field '%s' has no value in this test; give it one", field.getName())
                .isNotNull().isNotEqualTo(Boolean.FALSE).isNotEqualTo(0);
        }
        assertThat(copy.getId()).isNull();
    }

    private static List<Field> authoredFields() {
        return Arrays.stream(BaseEntityAttribute.class.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .filter(field -> !NOT_COPIED.contains(field.getName()))
            .toList();
    }
}
