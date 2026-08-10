package com.processpuzzle.artifact.usecase.exception;

public class ArtifactAlreadyExistsException extends RuntimeException {

    public ArtifactAlreadyExistsException(String orgKey, String artifactId) {
        super("Artifact '" + artifactId + "' already exists in organization '" + orgKey + "'");
    }
}
