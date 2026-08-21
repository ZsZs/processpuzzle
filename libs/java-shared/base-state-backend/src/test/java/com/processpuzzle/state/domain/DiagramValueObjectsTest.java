package com.processpuzzle.state.domain;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagramValueObjectsTest {

    @Test
    void nodeLayout_shouldRejectABlankStateKey() {
        Point position = new Point(0, 0);

        assertThatThrownBy(() -> new NodeLayout(null, position, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stateKey");
        assertThatThrownBy(() -> new NodeLayout("   ", position, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stateKey");
    }

    @Test
    void nodeLayout_shouldRejectAMissingPosition() {
        assertThatThrownBy(() -> new NodeLayout("draft", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("position");
    }

    /** Absent size is the normal case: a node auto-sized by its content. */
    @Test
    void nodeLayout_shouldAllowNoSize() {
        NodeLayout node = new NodeLayout("draft", new Point(12, 34), null);

        assertThat(node.stateKey()).isEqualTo("draft");
        assertThat(node.position()).isEqualTo(new Point(12, 34));
        assertThat(node.size()).isNull();
    }

    @Test
    void edgeLayout_shouldRejectABlankTransitionKey() {
        assertThatThrownBy(() -> new EdgeLayout(null, List.of(), null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transitionKey");
        assertThatThrownBy(() -> new EdgeLayout("  ", List.of(), null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transitionKey");
    }

    @Test
    void edgeLayout_shouldDefaultNullPointsToEmpty() {
        EdgeLayout edge = new EdgeLayout("submit", null, null, null, null);

        assertThat(edge.points()).isEmpty();
        assertThat(edge.sourcePort()).isNull();
        assertThat(edge.targetPort()).isNull();
        assertThat(edge.routing()).isNull();
    }

    @Test
    void edgeLayout_shouldCopyAndFreezeItsPoints() {
        List<Point> points = new ArrayList<>(List.of(new Point(1, 1)));
        EdgeLayout edge = new EdgeLayout("submit", points, "port-right", "port-left", "orthogonal");

        points.add(new Point(2, 2));

        assertThat(edge.points()).containsExactly(new Point(1, 1));
        assertThatThrownBy(() -> edge.points().add(new Point(3, 3)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void diagramDefinitionKey_shouldBeValueEqual() {
        DiagramDefinitionKey key = new DiagramDefinitionKey("org-1", "invoice");

        assertThat(key).isEqualTo(new DiagramDefinitionKey("org-1", "invoice"))
                .hasSameHashCodeAs(new DiagramDefinitionKey("org-1", "invoice"))
                .isNotEqualTo(new DiagramDefinitionKey("org-2", "invoice"))
                .isNotEqualTo(new DiagramDefinitionKey("org-1", "order"))
                .isNotEqualTo("org-1/invoice");
        assertThat(key).isEqualTo(key);
        assertThat(key.toString()).isEqualTo("org-1/invoice");
    }

    @Test
    void diagramDefinitionKey_shouldBeMutableForJpa() {
        DiagramDefinitionKey key = new DiagramDefinitionKey();
        key.setOrgKey("org-1");
        key.setEntityName("invoice");

        assertThat(key.getOrgKey()).isEqualTo("org-1");
        assertThat(key.getEntityName()).isEqualTo("invoice");
    }
}
