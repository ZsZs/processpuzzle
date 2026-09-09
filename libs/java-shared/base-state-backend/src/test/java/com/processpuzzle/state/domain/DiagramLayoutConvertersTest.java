package com.processpuzzle.state.domain;

import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagramLayoutConvertersTest {

    private final NodeLayoutsConverter nodesConverter = new NodeLayoutsConverter();
    private final EdgeLayoutsConverter edgesConverter = new EdgeLayoutsConverter();

    @Test
    void nodesConverter_convertToDatabaseColumn() {
        assertThat(nodesConverter.convertToDatabaseColumn(null)).isEqualTo("[]");

        NodeLayout node = new NodeLayout("draft", new Point(12.5, 34), new NodeSize(150, 60));
        String json = nodesConverter.convertToDatabaseColumn(List.of(node));

        assertThat(json)
                .contains("\"stateKey\":\"draft\"")
                .contains("\"x\":12.5")
                .contains("\"width\":150.0");
    }

    @Test
    void nodesConverter_convertToEntityAttribute() {
        assertThat(nodesConverter.convertToEntityAttribute(null)).isEmpty();
        assertThat(nodesConverter.convertToEntityAttribute("")).isEmpty();
        assertThat(nodesConverter.convertToEntityAttribute("   ")).isEmpty();

        String json = "[{\"stateKey\":\"draft\",\"position\":{\"x\":12.5,\"y\":34.0},"
                + "\"size\":{\"width\":150.0,\"height\":60.0}}]";
        List<NodeLayout> nodes = nodesConverter.convertToEntityAttribute(json);

        assertThat(nodes).containsExactly(new NodeLayout("draft", new Point(12.5, 34), new NodeSize(150, 60)));

        assertThatThrownBy(() -> nodesConverter.convertToEntityAttribute("{invalid json"))
                .isInstanceOf(UncheckedIOException.class);
    }

    /** An auto-sized node round-trips with no size at all, which is the common case. */
    @Test
    void nodesConverter_shouldRoundTripANodeWithNoSize() {
        NodeLayout node = new NodeLayout("draft", new Point(0, 0), null);

        String json = nodesConverter.convertToDatabaseColumn(List.of(node));

        assertThat(nodesConverter.convertToEntityAttribute(json)).containsExactly(node);
    }

    /**
     * A node row without a position fails the {@link NodeLayout} invariant, and the converter
     * surfaces that as its own read failure rather than letting a half-built record escape.
     */
    @Test
    void nodesConverter_shouldRejectARowWithNoPosition() {
        assertThatThrownBy(() -> nodesConverter.convertToEntityAttribute("[{\"stateKey\":\"draft\"}]"))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void edgesConverter_convertToDatabaseColumn() {
        assertThat(edgesConverter.convertToDatabaseColumn(null)).isEqualTo("[]");

        EdgeLayout edge = new EdgeLayout(
                "submit", List.of(new Point(1, 2)), "port-right", "port-left", "orthogonal");
        String json = edgesConverter.convertToDatabaseColumn(List.of(edge));

        assertThat(json)
                .contains("\"transitionKey\":\"submit\"")
                .contains("\"sourcePort\":\"port-right\"")
                .contains("\"routing\":\"orthogonal\"");
    }

    @Test
    void edgesConverter_convertToEntityAttribute() {
        assertThat(edgesConverter.convertToEntityAttribute(null)).isEmpty();
        assertThat(edgesConverter.convertToEntityAttribute("")).isEmpty();
        assertThat(edgesConverter.convertToEntityAttribute("   ")).isEmpty();

        String json = "[{\"transitionKey\":\"submit\",\"points\":[{\"x\":1.0,\"y\":2.0}],"
                + "\"sourcePort\":\"port-right\",\"targetPort\":\"port-left\",\"routing\":\"orthogonal\"}]";
        List<EdgeLayout> edges = edgesConverter.convertToEntityAttribute(json);

        assertThat(edges).containsExactly(new EdgeLayout(
                "submit", List.of(new Point(1, 2)), "port-right", "port-left", "orthogonal"));

        assertThatThrownBy(() -> edgesConverter.convertToEntityAttribute("{invalid json"))
                .isInstanceOf(UncheckedIOException.class);
    }

    /** An automatically routed edge stores only its key and ports. */
    @Test
    void edgesConverter_shouldRoundTripAnEdgeWithNoWaypoints() {
        EdgeLayout edge = new EdgeLayout("submit", List.of(), "port-right", "port-left", null);

        String json = edgesConverter.convertToDatabaseColumn(List.of(edge));

        assertThat(edgesConverter.convertToEntityAttribute(json)).containsExactly(edge);
    }
}
