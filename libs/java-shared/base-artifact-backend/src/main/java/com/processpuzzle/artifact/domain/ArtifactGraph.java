package com.processpuzzle.artifact.domain;

import java.util.List;
import java.util.Optional;

/**
 * The whole nested metadata of an artifact — input ports, output ports, and the flat block
 * list — as one immutable value, persisted as a single JSON document in one column (see
 * {@link ArtifactGraphConverter}). Same rationale {@code AppGraph} documents: nothing queries
 * inside the graph (the list endpoint returns header-only summaries), referential integrity is
 * validated in the service layer, not the relational model, and bundling everything into one
 * record keeps the converter's target type concrete.
 */
public record ArtifactGraph(
        List<ArtifactInputPort> inputPorts,
        List<ArtifactOutputPort> outputPorts,
        List<ArtifactBlock> blocks) {

    public ArtifactGraph {
        inputPorts = inputPorts == null ? List.of() : List.copyOf(inputPorts);
        outputPorts = outputPorts == null ? List.of() : List.copyOf(outputPorts);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    public static ArtifactGraph empty() {
        return new ArtifactGraph(List.of(), List.of(), List.of());
    }

    public Optional<ArtifactBlock> findBlock(String blockId) {
        return blocks.stream().filter(b -> b.id().equals(blockId)).findFirst();
    }

    /** Returns a copy of this graph with {@code blocks} replaced — used by the block-level use cases. */
    public ArtifactGraph withBlocks(List<ArtifactBlock> replacement) {
        return new ArtifactGraph(inputPorts, outputPorts, replacement);
    }

    /**
     * The mirror image of {@link #withBlocks}: both port lists replaced, blocks carried over
     * untouched. Used by {@code UpdateArtifactProperties}, which by contract cannot receive blocks.
     */
    public ArtifactGraph withPorts(List<ArtifactInputPort> newInputPorts, List<ArtifactOutputPort> newOutputPorts) {
        return new ArtifactGraph(newInputPorts, newOutputPorts, blocks);
    }
}
