package com.processpuzzle.document.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The shared JSON-column base, exercised through the three concrete converters that bind it. The
 * behaviour worth pinning is the tolerance: a null or blank column reads back as null rather than
 * blowing up, and a column written by a newer revision still loads.
 */
class JsonColumnConverterTest {

    private final DocumentContentConverter contentConverter = new DocumentContentConverter();
    private final DocumentRolesConverter rolesConverter = new DocumentRolesConverter();
    private final DocumentPortsConverter portsConverter = new DocumentPortsConverter();

    @Test
    void contentRoundTripsThroughTheColumn() {
        DocumentContent content = DocumentContent.of(List.of(
                new DocumentBlock("grid-1", BlockKind.WIDGET, true, null, WidgetPlacement.REFERENCED, "entity-grid",
                        Map.of("rows", 10), Map.of("rows", "customer"), Map.of())));

        DocumentContent read = contentConverter.convertToEntityAttribute(
                contentConverter.convertToDatabaseColumn(content));

        assertThat(read.blocks()).singleElement().satisfies(block -> {
            assertThat(block.id()).isEqualTo("grid-1");
            assertThat(block.kind()).isEqualTo(BlockKind.WIDGET);
            assertThat(block.placement()).isEqualTo(WidgetPlacement.REFERENCED);
            assertThat(block.props()).containsEntry("rows", 10);
            assertThat(block.inputBindings()).containsEntry("rows", "customer");
        });
    }

    @Test
    void rolesAndPortsRoundTripToo() {
        DocumentRoles roles = new DocumentRoles(List.of("reader"), List.of("editor"), List.of());
        DocumentPorts ports = new DocumentPorts(
                List.of(new DocumentInputPort("customer", PortType.ENTITY_REF, true, "The customer", null, "Customer",
                        new AttributeVisibility(AttributeVisibility.Mode.INCLUDE, List.of("name")), null)),
                List.of(new DocumentOutputPort("selection", PortType.ENTITY_COLLECTION, null, "Customer", null)));

        assertThat(rolesConverter.convertToEntityAttribute(rolesConverter.convertToDatabaseColumn(roles)))
                .isEqualTo(roles);
        assertThat(portsConverter.convertToEntityAttribute(portsConverter.convertToDatabaseColumn(ports)))
                .isEqualTo(ports);
    }

    @Test
    void aNullValueStaysNullInBothDirections() {
        assertThat(contentConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(contentConverter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void aBlankColumnReadsBackAsNullRatherThanFailing() {
        assertThat(contentConverter.convertToEntityAttribute("")).isNull();
        assertThat(contentConverter.convertToEntityAttribute("   ")).isNull();
    }

    @Test
    void aColumnWrittenByANewerRevisionStillLoads() {
        // Unknown properties are ignored on read, which is what makes adding a field a non-event.
        DocumentRoles read = rolesConverter.convertToEntityAttribute(
                "{\"readerRoles\":[\"reader\"],\"somethingAddedLater\":true}");

        assertThat(read.readerRoles()).containsExactly("reader");
    }

    @Test
    void unreadableJsonFailsLoudlyRatherThanSilentlyLosingTheColumn() {
        assertThatThrownBy(() -> contentConverter.convertToEntityAttribute("{ not json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot deserialize DocumentContent");
    }

    @Test
    void aValueThatCannotBeSerializedFailsLoudlyToo() {
        DocumentContent unserializable = DocumentContent.of(List.of(
                new DocumentBlock("intro", BlockKind.TEXT, true, null, null, null,
                        Map.of("bad", new Unserializable()), Map.of(), Map.of())));

        assertThatThrownBy(() -> contentConverter.convertToDatabaseColumn(unserializable))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot serialize DocumentContent");
    }

    /** A bean whose only property throws, so Jackson cannot write it. */
    static class Unserializable {
        public String getBoom() {
            throw new UnsupportedOperationException("not serializable");
        }
    }
}
