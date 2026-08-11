package com.processpuzzle.document.domain;

import java.util.List;
import java.util.Optional;

/**
 * The whole nested metadata of a document — input ports, output ports, and the flat block
 * list — as one immutable value, persisted as a single JSON document in one column (see
 * {@link DocumentGraphConverter}). Same rationale {@code AppGraph} documents: nothing queries
 * inside the graph (the list endpoint returns header-only summaries), referential integrity is
 * validated in the service layer, not the relational model, and bundling everything into one
 * record keeps the converter's target type concrete.
 */
public record DocumentGraph(
        List<DocumentInputPort> inputPorts,
        List<DocumentOutputPort> outputPorts,
        List<DocumentBlock> blocks) {

    public DocumentGraph {
        inputPorts = inputPorts == null ? List.of() : List.copyOf(inputPorts);
        outputPorts = outputPorts == null ? List.of() : List.copyOf(outputPorts);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    public static DocumentGraph empty() {
        return new DocumentGraph(List.of(), List.of(), List.of());
    }

    public Optional<DocumentBlock> findBlock(String blockId) {
        return blocks.stream().filter(b -> b.id().equals(blockId)).findFirst();
    }

    /** Returns a copy of this graph with {@code blocks} replaced — used by the block-level use cases. */
    public DocumentGraph withBlocks(List<DocumentBlock> replacement) {
        return new DocumentGraph(inputPorts, outputPorts, replacement);
    }

    /**
     * The mirror image of {@link #withBlocks}: both port lists replaced, blocks carried over
     * untouched. Used by {@code UpdateDocumentProperties}, which by contract cannot receive blocks.
     */
    public DocumentGraph withPorts(List<DocumentInputPort> newInputPorts, List<DocumentOutputPort> newOutputPorts) {
        return new DocumentGraph(newInputPorts, newOutputPorts, blocks);
    }
}
