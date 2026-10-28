package com.processpuzzle.app.adapter.inbound;

import java.util.Optional;

/**
 * Where a file of a starter bundle that belongs to a module or an app says it belongs: the path is
 * {@code <group>/<owner kind>/<owner key>/<rest>}, as in {@code assets/modules/plan-race/docs/guide.md} or
 * {@code assets/apps/app/cards/plan.svg}. The first segment is the manifest group the file is listed under and is
 * not looked at, so a bundle may keep its assets wherever it likes as long as the rest of the path is this.
 *
 * <p>Carried by the path rather than by a file of its own, so that a bundle is its files and nothing says the same
 * thing twice. {@link #key()} is what the importer records for the file: the path without its first segment.
 *
 * @param ownerKind {@code apps} or {@code modules}
 * @param ownerKey  the app id or module key
 * @param rest      the path inside the owner, which may hold further {@code /}
 */
record BundleOwnedPath(String ownerKind, String ownerKey, String rest) {

    static final String APPS = "apps";
    static final String MODULES = "modules";

    /** Empty unless the path has a group, an owner kind of {@link #APPS} or {@link #MODULES}, an owner and a file. */
    static Optional<BundleOwnedPath> parse(String path) {
        String[] segments = path == null ? new String[0] : path.split("/");
        if (segments.length < 4 || !(APPS.equals(segments[1]) || MODULES.equals(segments[1]))) {
            return Optional.empty();
        }
        return Optional.of(new BundleOwnedPath(segments[1], segments[2],
                String.join("/", java.util.Arrays.copyOfRange(segments, 3, segments.length))));
    }

    boolean isApp() {
        return APPS.equals(ownerKind);
    }

    /** What the importer records the file under. */
    String key() {
        return ownerKind + "/" + ownerKey + "/" + rest;
    }

    /** The file's own name: the last segment of {@link #rest()}. */
    String fileName() {
        return rest.substring(rest.lastIndexOf('/') + 1);
    }
}
