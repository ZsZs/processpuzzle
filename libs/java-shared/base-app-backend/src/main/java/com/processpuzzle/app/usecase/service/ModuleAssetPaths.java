package com.processpuzzle.app.usecase.service;

import com.processpuzzle.app.usecase.exception.ModuleAssetRejectedException;
import java.util.regex.Pattern;

/**
 * The shape of an asset path: relative to the module, segments of letters, digits, {@code .}, {@code _} and
 * {@code -}. Narrow on purpose — the path is a URL query value, a markdown link target and part of what the
 * frontend resolves relative links against, and none of those should have to agree on an escaping.
 */
public final class ModuleAssetPaths {

    public static final int MAX_LENGTH = 200;

    private static final Pattern PATH = Pattern.compile("[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*");

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
        if (!PATH.matcher(path).matches() || isDotSegmentIn(path)) {
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

    private static boolean isDotSegmentIn(String path) {
        for (String segment : path.split("/")) {
            if (segment.chars().allMatch(c -> c == '.')) {
                return true;
            }
        }
        return false;
    }
}
