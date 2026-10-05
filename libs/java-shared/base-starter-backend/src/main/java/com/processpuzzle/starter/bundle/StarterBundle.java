package com.processpuzzle.starter.bundle;

import java.util.Map;

/**
 * A bundle that passed {@link BundleReader}: its manifest, and every file the manifest lists, by path.
 */
public record StarterBundle(StarterManifest manifest, Map<String, byte[]> files) {

    public StarterBundle {
        files = Map.copyOf(files);
    }

    public byte[] file(String path) {
        return files.get(path);
    }
}
