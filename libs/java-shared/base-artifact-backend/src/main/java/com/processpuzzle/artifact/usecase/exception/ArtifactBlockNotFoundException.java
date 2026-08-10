package com.processpuzzle.artifact.usecase.exception;

public class ArtifactBlockNotFoundException extends RuntimeException {

    public ArtifactBlockNotFoundException(String orgKey, String artifactId, String blockId) {
        super("No block '" + blockId + "' in artifact '" + artifactId + "' (organization '" + orgKey + "')");
    }
}
