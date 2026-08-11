package com.processpuzzle.document.domain;

import java.util.List;
import java.util.Optional;

/**
 * One locale's block list as a single immutable value, persisted as one JSON column (see
 * {@link DocumentContentConverter}). Same rationale {@code AppGraph} documents: nothing queries
 * inside the content, referential integrity is validated in the service layer rather than the
 * relational model, and a record keeps the converter's target type concrete instead of a raw
 * {@code List}.
 *
 * <p>This is the successor of the former {@code DocumentGraph}, which also carried the port
 * declarations. Ports moved to {@link Document} because they are language-invariant structural
 * wiring: every translation's widget bindings must resolve against the same declarations, so
 * holding a copy per locale would let them drift. What is left here is content, which is exactly
 * what is per-locale and exactly what gets published — hence the name.
 */
public record DocumentContent(List<DocumentBlock> blocks) {

    public DocumentContent {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    public static DocumentContent empty() {
        return new DocumentContent(List.of());
    }

    public static DocumentContent of(List<DocumentBlock> blocks) {
        return new DocumentContent(blocks);
    }

    public Optional<DocumentBlock> findBlock(String blockId) {
        return blocks.stream().filter(block -> blockId.equals(block.id())).findFirst();
    }

    public DocumentContent withBlocks(List<DocumentBlock> replacement) {
        return new DocumentContent(replacement);
    }

    /** The widget block ids declared here — what cross-locale drift detection compares. */
    public List<String> widgetBlockIds() {
        return blocks.stream().filter(DocumentBlock::isWidget).map(DocumentBlock::id).toList();
    }
}
