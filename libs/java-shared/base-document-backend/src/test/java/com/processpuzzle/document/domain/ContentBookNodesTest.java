package com.processpuzzle.document.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The tree invariants a book can never be stored without, and what deleting a document does to it. */
class ContentBookNodesTest {

    @Test
    void siblingsAreRenumberedInTheirGivenOrderAndHeldDepthFirst() {
        ContentBookNodes tree = new ContentBookNodes(List.of(
                node("b", "doc-b", null, 7),
                node("a-1", "doc-a1", "a", 3),
                node("a", "doc-a", null, 2)));

        assertThat(tree.nodes()).containsExactly(
                node("a", "doc-a", null, 0),
                node("a-1", "doc-a1", "a", 0),
                node("b", "doc-b", null, 1));
    }

    @Test
    void equalIndexesKeepTheOrderTheyWereGivenIn() {
        ContentBookNodes tree = new ContentBookNodes(List.of(node("x", "doc-x", null, 0), node("y", "doc-y", null, 0)));

        assertThat(tree.nodes()).extracting(ContentBookNode::id).containsExactly("x", "y");
    }

    @Test
    void anUnknownParentIsRefused() {
        List<ContentBookNode> given = List.of(node("a", "doc-a", "ghost", 0));
        assertThatThrownBy(() -> new ContentBookNodes(given))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown parent 'ghost'");
    }

    @Test
    void aCycleIsRefused() {
        List<ContentBookNode> given = List.of(node("a", "doc-a", "b", 0), node("b", "doc-b", "a", 0));
        assertThatThrownBy(() -> new ContentBookNodes(given))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void aDocumentAppearsAtMostOnce() {
        List<ContentBookNode> given = List.of(node("a", "doc", null, 0), node("b", "doc", null, 1));
        assertThatThrownBy(() -> new ContentBookNodes(given))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Document 'doc' appears more than once");
    }

    @Test
    void aNodeIdAppearsAtMostOnce() {
        List<ContentBookNode> given = List.of(node("a", "doc-1", null, 0), node("a", "doc-2", null, 1));
        assertThatThrownBy(() -> new ContentBookNodes(given))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Node id 'a'");
    }

    @Test
    void everyNodeNeedsAnIdAndADocument() {
        List<ContentBookNode> given = List.of(node(" ", "doc", null, 0));
        assertThatThrownBy(() -> new ContentBookNodes(given))
                .isInstanceOf(IllegalArgumentException.class);
        List<ContentBookNode> given2 = List.of(node("a", null, null, 0));
        assertThatThrownBy(() -> new ContentBookNodes(given2))
                .isInstanceOf(IllegalArgumentException.class);
        List<ContentBookNode> given3 = Arrays.asList((ContentBookNode) null);
        assertThatThrownBy(() -> new ContentBookNodes(given3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMissingListIsAnEmptyTree() {
        assertThat(new ContentBookNodes(null).nodes()).isEmpty();
        assertThat(ContentBookNodes.empty().documentIds()).isEmpty();
    }

    @Test
    void detachMovesTheChildrenUpIntoThePlaceOfTheRemovedNode() {
        ContentBookNodes tree = new ContentBookNodes(List.of(
                node("first", "doc-first", null, 0),
                node("gone", "doc-gone", null, 1),
                node("gone-1", "doc-gone-1", "gone", 0),
                node("gone-2", "doc-gone-2", "gone", 1),
                node("gone-2-1", "doc-gone-2-1", "gone-2", 0),
                node("last", "doc-last", null, 2)));

        ContentBookNodes detached = tree.detach("doc-gone");

        assertThat(detached.nodes()).containsExactly(
                node("first", "doc-first", null, 0),
                node("gone-1", "doc-gone-1", null, 1),
                node("gone-2", "doc-gone-2", null, 2),
                node("gone-2-1", "doc-gone-2-1", "gone-2", 0),
                node("last", "doc-last", null, 3));
        assertThat(detached.references("doc-gone")).isFalse();
    }

    @Test
    void detachingADocumentTheBookDoesNotShowChangesNothing() {
        ContentBookNodes tree = new ContentBookNodes(List.of(node("a", "doc-a", null, 0)));

        assertThat(tree.detach("elsewhere")).isSameAs(tree);
    }

    @Test
    void detachingANestedNodeKeepsUnrelatedBranches() {
        ContentBookNodes tree = new ContentBookNodes(List.of(
                node("a", "doc-a", null, 0),
                node("a-1", "doc-a1", "a", 0),
                node("b", "doc-b", null, 1),
                node("b-1", "doc-b1", "b", 0)));

        assertThat(tree.detach("doc-a1").nodes()).containsExactly(
                node("a", "doc-a", null, 0),
                node("b", "doc-b", null, 1),
                node("b-1", "doc-b1", "b", 0));
    }

    @Test
    void theColumnRoundTripsWithoutExtraProperties() {
        ContentBookNodesConverter converter = new ContentBookNodesConverter();
        ContentBookNodes tree = new ContentBookNodes(List.of(node("a", "doc-a", null, 0), node("b", "doc-b", "a", 0)));

        String json = converter.convertToDatabaseColumn(tree);

        assertThat(json).isEqualTo("{\"nodes\":[{\"id\":\"a\",\"documentId\":\"doc-a\",\"orderIndex\":0},"
                + "{\"id\":\"b\",\"documentId\":\"doc-b\",\"parentId\":\"a\",\"orderIndex\":0}]}");
        assertThat(converter.convertToEntityAttribute(json)).isEqualTo(tree);
    }

    private static ContentBookNode node(String id, String documentId, String parentId, int orderIndex) {
        return new ContentBookNode(id, documentId, parentId, orderIndex);
    }
}
