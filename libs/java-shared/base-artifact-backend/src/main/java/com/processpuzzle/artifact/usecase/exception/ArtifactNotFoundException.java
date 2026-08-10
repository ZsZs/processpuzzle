package com.processpuzzle.artifact.usecase.exception;

public class ArtifactNotFoundException extends RuntimeException {

    public ArtifactNotFoundException(String orgKey, String artifactId) {
        super("No artifact '" + artifactId + "' in organization '" + orgKey + "'");
    }
}
