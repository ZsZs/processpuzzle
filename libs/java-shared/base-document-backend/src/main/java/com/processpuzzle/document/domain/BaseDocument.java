package com.processpuzzle.document.domain;

public class BaseDocument {
    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final String name;
    private final String contentType;

    public BaseDocument(String name) {
        this(name, DEFAULT_CONTENT_TYPE);
    }

    public BaseDocument(String name, String contentType) {
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
