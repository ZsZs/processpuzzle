package com.processpuzzle.app.usecase;

import java.util.Arrays;
import java.util.Objects;

/**
 * An asset with its bytes, for serving. {@code sha256} doubles as the entity tag.
 *
 * <p>Equality and hashing look at the bytes, not at the array reference, and {@code toString} leaves them out
 * so that a log line never carries a file's content.
 */
public record ModuleAssetContent(String path, String mimeType, String sha256, byte[] content) {

    @Override
    public boolean equals(Object other) {
        return other instanceof ModuleAssetContent that
                && Objects.equals(path, that.path)
                && Objects.equals(mimeType, that.mimeType)
                && Objects.equals(sha256, that.sha256)
                && Arrays.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, mimeType, sha256, Arrays.hashCode(content));
    }

    @Override
    public String toString() {
        return "ModuleAssetContent[path=" + path + ", mimeType=" + mimeType + ", sha256=" + sha256
                + ", size=" + (content == null ? 0 : content.length) + "]";
    }
}
