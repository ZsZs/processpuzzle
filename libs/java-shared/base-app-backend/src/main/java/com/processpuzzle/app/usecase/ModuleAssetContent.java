package com.processpuzzle.app.usecase;

/** An asset with its bytes, for serving. {@code sha256} doubles as the entity tag. */
public record ModuleAssetContent(String path, String mimeType, String sha256, byte[] content) {
}
