package com.processpuzzle.app.usecase;

/** One asset of a module as the lists show it: where it is, what it is, and the hash that names its content. */
public record ModuleAssetSummary(String path, String mimeType, long size, String sha256) {
}
