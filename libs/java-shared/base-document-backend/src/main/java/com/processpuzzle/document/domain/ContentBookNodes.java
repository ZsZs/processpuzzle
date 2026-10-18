package com.processpuzzle.document.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A {@link ContentBook}'s tree as one immutable value, persisted as one JSON column (see
 * {@link ContentBookNodesConverter}).
 *
 * <p>Valid by construction: every {@code parentId} names a node of the same book, the parent links
 * form no cycle, and a document appears at most once — a book is a table of contents, and the same
 * page twice in one would make "where am I" ambiguous for the reader. Construction also
 * <em>normalizes</em>: siblings are renumbered 0..n-1 in their given order and the list is held in
 * depth-first order, so two trees that read the same are equal and a client may send any gaps it
 * likes.
 *
 * <p>An invalid tree is an {@link IllegalArgumentException}; the use cases translate it into their own
 * 400, because the domain does not know about the API.
 *
 * <p>No {@code getX}/{@code isX} methods besides the record component: Jackson would persist them as
 * extra properties of the column.
 */
public record ContentBookNodes(List<ContentBookNode> nodes) {

    public ContentBookNodes {
        nodes = normalize(nodes == null ? List.of() : nodes);
    }

    public static ContentBookNodes empty() {
        return new ContentBookNodes(List.of());
    }

    public List<String> documentIds() {
        return nodes.stream().map(ContentBookNode::documentId).toList();
    }

    public boolean references(String documentId) {
        return nodes.stream().anyMatch(node -> node.documentId().equals(documentId));
    }

    /**
     * The tree without the node showing {@code documentId}; its children move up into its place, in
     * their order. What deleting a document does to every book that shows it — the document is gone,
     * but the pages filed under it are not.
     */
    public ContentBookNodes detach(String documentId) {
        ContentBookNode removed = nodes.stream()
                .filter(node -> node.documentId().equals(documentId))
                .findFirst()
                .orElse(null);
        if (removed == null) {
            return this;
        }

        List<ContentBookNode> result = new ArrayList<>();
        int position = 0;
        for (ContentBookNode sibling : childrenOf(removed.parentId())) {
            if (sibling == removed) {
                for (ContentBookNode child : childrenOf(removed.id())) {
                    result.add(child.withPlace(removed.parentId(), position++));
                }
            } else {
                result.add(sibling.withPlace(sibling.parentId(), position++));
            }
        }
        nodes.stream()
                .filter(node -> node != removed && !Objects.equals(node.parentId(), removed.parentId())
                        && !Objects.equals(node.parentId(), removed.id()))
                .forEach(result::add);
        return new ContentBookNodes(result);
    }

    private List<ContentBookNode> childrenOf(String parentId) {
        return nodes.stream().filter(node -> Objects.equals(node.parentId(), parentId)).toList();
    }

    private static List<ContentBookNode> normalize(List<ContentBookNode> given) {
        Set<String> nodeIds = new HashSet<>();
        Set<String> documentIds = new HashSet<>();
        for (ContentBookNode node : given) {
            if (node == null || isBlank(node.id()) || isBlank(node.documentId())) {
                throw new IllegalArgumentException("Every node needs an id and a documentId");
            }
            if (!nodeIds.add(node.id())) {
                throw new IllegalArgumentException("Node id '" + node.id() + "' appears more than once");
            }
            if (!documentIds.add(node.documentId())) {
                throw new IllegalArgumentException("Document '" + node.documentId() + "' appears more than once");
            }
        }

        Map<String, List<ContentBookNode>> byParent = new HashMap<>();
        for (ContentBookNode node : given) {
            if (node.parentId() != null && !nodeIds.contains(node.parentId())) {
                throw new IllegalArgumentException(
                        "Node '" + node.id() + "' names an unknown parent '" + node.parentId() + "'");
            }
            byParent.computeIfAbsent(node.parentId(), key -> new ArrayList<>()).add(node);
        }
        // A stable sort: equal indexes keep the order they were given in.
        byParent.values().forEach(siblings -> siblings.sort(Comparator.comparingInt(ContentBookNode::orderIndex)));

        Map<String, ContentBookNode> ordered = new LinkedHashMap<>();
        appendSubtree(null, byParent, ordered);
        if (ordered.size() != given.size()) {
            // Every parent exists, so a node the walk from the roots cannot reach sits on a cycle.
            throw new IllegalArgumentException("The parent links of the tree form a cycle");
        }
        return List.copyOf(ordered.values());
    }

    private static void appendSubtree(String parentId, Map<String, List<ContentBookNode>> byParent,
                                      Map<String, ContentBookNode> ordered) {
        List<ContentBookNode> siblings = byParent.getOrDefault(parentId, List.of());
        for (int index = 0; index < siblings.size(); index++) {
            ContentBookNode node = siblings.get(index);
            ordered.put(node.id(), node.withPlace(parentId, index));
            appendSubtree(node.id(), byParent, ordered);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
