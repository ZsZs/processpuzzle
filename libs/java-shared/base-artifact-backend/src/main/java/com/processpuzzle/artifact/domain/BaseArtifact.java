package com.processpuzzle.artifact.domain;

public class BaseArtifact {
    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final String name;
    private final String contentType;

    public BaseArtifact(String name) {
        this(name, DEFAULT_CONTENT_TYPE);
    }

    public BaseArtifact(String name, String contentType) {
        this.name = name;
        this.contentType = contentType;
    }

    public String getName() {
        return name;
    }

    public String getContentType() {
        return contentType;
    }

    public boolean isBinary() {
        return DEFAULT_CONTENT_TYPE.equals(contentType);
    }

    public String describe() {
        return isBinary() ? name : name + " (" + contentType + ")";
    }
}
