package com.processpuzzle.app.usecase.service;

import com.processpuzzle.app.usecase.exception.ModuleAssetRejectedException;

/**
 * The shape of an asset path: relative to the module, segments of letters, digits, {@code .}, {@code _} and
 * {@code -}. Narrow on purpose — the path is a URL query value, a markdown link target and part of what the
 * frontend resolves relative links against, and none of those should have to agree on an escaping.
 */
public final class ModuleAssetPaths {

    public static final int MAX_LENGTH = 200;

    private ModuleAssetPaths() {
    }

    /** The path as it is stored, or a rejection naming what is wrong with it. */
    public static String require(String path) {
        if (path == null || path.isBlank()) {
            throw new ModuleAssetRejectedException("The asset needs a path.");
        }
        if (path.length() > MAX_LENGTH) {
            throw new ModuleAssetRejectedException("The asset path is longer than " + MAX_LENGTH + " characters.");
        }
        if (!isValid(path)) {
            throw new ModuleAssetRejectedException("'" + path + "' is not a valid asset path: use segments of letters, "
                    + "digits, '.', '_' and '-' separated by '/', without '.' or '..' segments.");
        }
        return path;
    }

    /** What a path defaults to when the upload names none: the file's own name, without any directory. */
    public static String fileNameOf(String originalFilename) {
        if (originalFilename == null) {
            return null;
        }
        int slash = Math.max(originalFilename.lastIndexOf('/'), originalFilename.lastIndexOf('\\'));
        return originalFilename.substring(slash + 1);
    }

    /** Segments separated by single slashes, none empty, none made of dots alone, each of the allowed characters. */
    private static boolean isValid(String path) {
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.chars().allMatch(c -> c == '.') || !segment.chars().allMatch(ModuleAssetPaths::isAllowed)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAllowed(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
    }
}
