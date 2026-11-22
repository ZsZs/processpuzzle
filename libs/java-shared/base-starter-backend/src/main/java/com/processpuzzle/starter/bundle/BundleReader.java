package com.processpuzzle.starter.bundle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.starter.StarterProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

/**
 * Reads an uploaded zip into a {@link StarterBundle}, treating it as untrusted input throughout.
 *
 * <p>Nothing is extracted to disk; entries are read into memory under the
 * {@link StarterProperties.Bundle} limits, which bound the <em>uncompressed</em> size, so a zip bomb
 * is cut off at the limit rather than inflated. Entry names are checked against a strict relative-path
 * pattern, which rules out traversal ({@code ../}), absolute paths and backslashes. Every file must be
 * listed by the manifest, every listed file must be present, and when the manifest carries
 * {@code integrity} hashes each one must match.
 */
@Component
public class BundleReader {

    public static final String MANIFEST = "manifest.yaml";
    public static final Set<Integer> SUPPORTED_SCHEMA_VERSIONS = Set.of(1);
    public static final Set<Integer> SUPPORTED_MANIFEST_VERSIONS = Set.of(1, 2, 3);

    /** What a {@code manifestVersion: 2} manifest may say at its top level; anything else is a misspelling. */
    private static final Set<String> V2_FIELDS = Set.of("manifestVersion", "kind", "id", "name", "version", "description",
            "category", "tags", "author", "license", "icon", "definitionSchemaVersion", "platform", "locales", "common",
            "contents", "migration", "migrations", "integrity");
    /** Version 3 adds the domain-starter fields (business-starters design, decision 11). */
    private static final Set<String> V3_FIELDS = union(V2_FIELDS, Set.of("keyPrefix", "mounts"));
    private static final Set<String> COMMON_FIELDS = Set.of("id", "minVersion");
    private static final String LAUNCHER = "launcher";
    private static final Set<String> MOUNT_FIELDS = Set.of("moduleKey", "basePath", "nav", LAUNCHER);
    private static final Set<String> NAV_FIELDS = Set.of("icon", "labelKey");
    private static final Set<String> LAUNCHER_FIELDS = Set.of("image", "titleKey", "subtitleKey", "contentKeys", "link");
    /** Short, lower case, no dash: the dash is what separates it from the rest of a key. */
    private static final Pattern KEY_PREFIX = Pattern.compile("[a-z][a-z0-9]{1,15}");
    /**
     * A base path is lower-case segments joined by '/', each made of words joined by '-', such as plan-race/overview. Checked
     * as a character set, no two separators in a row and none at either end, rather than as a nested repetition, which
     * the regex engine matches recursively.
     */
    private static final Pattern BASE_PATH_CHARACTERS = Pattern.compile("[a-z0-9/-]+");
    private static final Pattern ADJACENT_SEPARATORS = Pattern.compile("[/-]{2}");
    private static final String MODULE_ASSET = "module:";
    /** The owner kind of an application's translations and assets: {@code <group>/apps/<app id>/<path>}. */
    private static final String APP_OWNED = "apps";
    private static final Pattern SEMVER = Pattern.compile("\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.-]+)?");

    private static final Pattern SAFE_PATH =
            Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]*(/[A-Za-z0-9_-][A-Za-z0-9._-]*)*");

    private final StarterProperties.Bundle limits;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public BundleReader(StarterProperties properties) {
        this.limits = properties.getBundle();
    }

    public StarterBundle read(InputStream input) {
        Map<String, byte[]> entries = readEntries(input);
        byte[] manifestBytes = entries.remove(MANIFEST);
        if (manifestBytes == null) {
            throw BundleRejectedException.invalid(null, "The bundle has no " + MANIFEST + " at its root.");
        }
        StarterManifest manifest = parseManifest(manifestBytes);
        validateManifest(manifest);
        validateFileList(manifest, entries);
        validateMigrations(manifest, entries);
        verifyIntegrity(manifest, entries);
        return new StarterBundle(manifest, entries);
    }

    private Map<String, byte[]> readEntries(InputStream input) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long total = 0;
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > limits.getMaxEntries()) {
                    throw BundleRejectedException.tooLarge(
                            "The bundle has more than " + limits.getMaxEntries() + " entries.");
                }
                String name = entry.getName();
                if (entry.isDirectory()) {
                    requireSafePath(name.substring(0, name.length() - 1));
                    continue;
                }
                requireSafePath(name);
                byte[] content = readCapped(zip, name);
                total += content.length;
                if (total > limits.getMaxTotalBytes()) {
                    throw BundleRejectedException.tooLarge(
                            "The bundle uncompresses to more than " + limits.getMaxTotalBytes() + " bytes.");
                }
                if (entries.put(name, content) != null) {
                    throw BundleRejectedException.invalid(name, "The bundle holds '" + name + "' twice.");
                }
            }
        } catch (ZipException e) {
            throw BundleRejectedException.invalid(null, "Not a readable zip: " + e.getMessage());
        } catch (IOException e) {
            throw BundleRejectedException.invalid(null, "Unable to read the bundle: " + e.getMessage());
        }
        if (count == 0) {
            throw BundleRejectedException.invalid(null, "The upload is empty or not a zip.");
        }
        return entries;
    }

    private byte[] readCapped(ZipInputStream zip, String name) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long size = 0;
        int read;
        while ((read = zip.read(buffer)) != -1) {
            size += read;
            if (size > limits.getMaxFileBytes()) {
                throw BundleRejectedException.tooLarge(
                        "'" + name + "' uncompresses to more than " + limits.getMaxFileBytes() + " bytes.");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private StarterManifest parseManifest(byte[] manifestBytes) {
        StarterManifest manifest;
        try {
            JsonNode tree = yamlMapper.readTree(manifestBytes);
            if (tree == null || tree.isNull() || tree.isMissingNode()) {
                throw BundleRejectedException.invalid(MANIFEST, MANIFEST + " is empty.");
            }
            requireKnownFieldsFromVersion2(tree);
            manifest = yamlMapper.treeToValue(tree, StarterManifest.class);
        } catch (IOException e) {
            throw BundleRejectedException.invalid(MANIFEST, MANIFEST + " is not valid: " + e.getMessage());
        }
        return manifest;
    }

    /** Version 1 ignored unknown fields; from version 2 a misspelt block fails the bundle instead of being dropped. */
    private void requireKnownFieldsFromVersion2(JsonNode tree) {
        int version = tree.path("manifestVersion").asInt(1);
        if (version < 2) {
            return;
        }
        unknownField(tree, version >= 3 ? V3_FIELDS : V2_FIELDS, "");
        if (tree.path("common").isObject()) {
            unknownField(tree.get("common"), COMMON_FIELDS, "common.");
        }
        JsonNode mounts = tree.path("mounts");
        for (int index = 0; index < mounts.size(); index++) {
            requireKnownMountFields(mounts.get(index), "mounts[" + index + "].");
        }
    }

    private void requireKnownMountFields(JsonNode mount, String prefix) {
        unknownField(mount, MOUNT_FIELDS, prefix);
        if (mount.path("nav").isObject()) {
            unknownField(mount.get("nav"), NAV_FIELDS, prefix + "nav.");
        }
        if (mount.path(LAUNCHER).isObject()) {
            unknownField(mount.get(LAUNCHER), LAUNCHER_FIELDS, prefix + LAUNCHER + ".");
        }
    }

    private void unknownField(JsonNode node, Set<String> allowed, String prefix) {
        for (String name : (Iterable<String>) node::fieldNames) {
            if (!allowed.contains(name)) {
                throw BundleRejectedException.invalid(MANIFEST, MANIFEST + " has an unknown field '" + prefix + name
                        + "' (accepted here: " + String.join(", ", new java.util.TreeSet<>(allowed)) + ").");
            }
        }
    }

    private void validateManifest(StarterManifest manifest) {
        if (isBlank(manifest.id()) || isBlank(manifest.version())) {
            throw BundleRejectedException.invalid(MANIFEST, MANIFEST + " must declare 'id' and 'version'.");
        }
        if (manifest.manifestVersion() != null && !SUPPORTED_MANIFEST_VERSIONS.contains(manifest.manifestVersion())) {
            throw BundleRejectedException.invalid(MANIFEST, "Unsupported manifestVersion " + manifest.manifestVersion()
                    + "; supported: " + SUPPORTED_MANIFEST_VERSIONS + ".");
        }
        validateKind(manifest);
        validateDomainStarter(manifest);
        if (manifest.definitionSchemaVersion() == null
                || !SUPPORTED_SCHEMA_VERSIONS.contains(manifest.definitionSchemaVersion())) {
            throw BundleRejectedException.invalid(MANIFEST, "Unsupported definitionSchemaVersion "
                    + manifest.definitionSchemaVersion() + "; supported: " + SUPPORTED_SCHEMA_VERSIONS + ".");
        }
        manifest.contents().values().forEach(paths -> paths.forEach(this::requireSafePath));
        manifest.migrations().forEach(this::requireSafePath);
    }

    /** The commons fields, which exist from manifestVersion 2: a common names its own organization, a tenant its common. */
    private void validateKind(StarterManifest manifest) {
        boolean v2 = manifest.manifestVersion() != null && manifest.manifestVersion() >= 2;
        if (!v2) {
            if (manifest.kind() != null || manifest.common() != null || !manifest.migrations().isEmpty()) {
                throw BundleRejectedException.invalid(MANIFEST, "'kind', 'common' and 'migrations' need manifestVersion 2.");
            }
            if (manifest.keyPrefix() != null || !manifest.mounts().isEmpty()) {
                throw BundleRejectedException.invalid(MANIFEST, "'keyPrefix' and 'mounts' need manifestVersion 3.");
            }
            return;
        }
        if (manifest.kind() != null && !"starter".equals(manifest.kind()) && !manifest.isCommon()) {
            throw BundleRejectedException.invalid(MANIFEST, "Unknown kind '" + manifest.kind() + "'; use 'starter' or 'common'.");
        }
        if (manifest.isCommon()) {
            validateCommon(manifest);
        } else if (manifest.common() != null) {
            validateTenantCommon(manifest);
        }
    }

    private void validateCommon(StarterManifest manifest) {
        if (!CommonKeys.isCommonId(manifest.id())) {
            throw BundleRejectedException.invalid(MANIFEST, "A common's id must be '<starter id>" + CommonKeys.SUFFIX + "'.");
        }
        if (manifest.common() != null) {
            throw BundleRejectedException.invalid(MANIFEST, "A common has no 'common' block of its own.");
        }
    }

    private void validateTenantCommon(StarterManifest manifest) {
        StarterManifest.Common common = manifest.common();
        if (!CommonKeys.commonOf(manifest.id()).equals(common.id())) {
            throw BundleRejectedException.invalid(MANIFEST, "'common.id' must be '" + CommonKeys.commonOf(manifest.id()) + "'.");
        }
        if (common.minVersion() == null || !SEMVER.matcher(common.minVersion()).matches()) {
            throw BundleRejectedException.invalid(MANIFEST, "'common.minVersion' must be a version such as 1.0.0.");
        }
    }

    /**
     * A version 3 manifest is a domain starter: it declares its key prefix, carries no application — neither an
     * {@code apps} group nor translations or assets an application owns — and its mount hints are well formed.
     * Whether the keys really start with the prefix, and the mounts name the bundle's modules, is checked by the
     * import, which asks each feature for its keys ({@code KeyPrefixRules}).
     */
    private void validateDomainStarter(StarterManifest manifest) {
        if (!manifest.isDomainStarter()) {
            return;
        }
        if (manifest.keyPrefix() == null || !KEY_PREFIX.matcher(manifest.keyPrefix()).matches()) {
            throw BundleRejectedException.invalid(MANIFEST, "A manifestVersion 3 manifest needs a 'keyPrefix' of 2 to 16 "
                    + "lower-case letters and digits, starting with a letter.");
        }
        if (manifest.contents().containsKey("apps")) {
            throw BundleRejectedException.invalid(MANIFEST, "A manifestVersion 3 starter carries no application ('apps'); "
                    + "its modules are mounted into the customer's application from 'mounts'.");
        }
        for (String group : List.of("translations", "assets")) {
            for (String path : manifest.contents().getOrDefault(group, List.of())) {
                String[] segments = path.split("/");
                if (segments.length > 1 && APP_OWNED.equals(segments[1])) {
                    throw BundleRejectedException.invalid(path, "'" + path + "' belongs to an application, which a "
                            + "manifestVersion 3 starter does not carry; give it to a module.");
                }
            }
        }
        if (manifest.isCommon() && !manifest.mounts().isEmpty()) {
            throw BundleRejectedException.invalid(MANIFEST, "A common is mounted nowhere, so it has no 'mounts'.");
        }
        Set<String> modules = new LinkedHashSet<>();
        Set<String> paths = new LinkedHashSet<>();
        manifest.mounts().forEach(mount -> validateMount(mount, modules, paths));
        requireOneEntryMount(manifest.mounts());
    }

    /**
     * A starter may bring several modules, but it adds one sidenav entry and one launcher card to the customer's
     * application, not one per module: at most one mount carries a {@code nav} or a {@code launcher}. The other mounts
     * only give their module a route, which the entry module's pages can link to.
     */
    private void requireOneEntryMount(List<StarterManifest.Mount> mounts) {
        List<String> entries = mounts.stream()
                .filter(mount -> mount.nav() != null || mount.launcher() != null)
                .map(StarterManifest.Mount::moduleKey)
                .toList();
        if (entries.size() > 1) {
            throw BundleRejectedException.invalid(MANIFEST, "A starter adds one sidenav entry and one launcher card, so only one "
                    + "mount carries a 'nav' or a 'launcher'; " + String.join(", ", entries) + " all do. Give the other modules "
                    + "mounts without them and link to their pages from the entry module.");
        }
    }

    private void validateMount(StarterManifest.Mount mount, Set<String> modules, Set<String> paths) {
        if (isBlank(mount.moduleKey()) || !modules.add(mount.moduleKey())) {
            throw BundleRejectedException.invalid(MANIFEST, "Every mount names a module, each module once"
                    + (isBlank(mount.moduleKey()) ? "." : "; '" + mount.moduleKey() + "' is named twice."));
        }
        String where = "The mount of '" + mount.moduleKey() + "'";
        if (!isBasePath(mount.basePath()) || !paths.add(mount.basePath())) {
            throw BundleRejectedException.invalid(MANIFEST, where + " needs a 'basePath' of its own, of lower-case "
                    + "segments such as plan-race.");
        }
        if (mount.nav() != null && isBlank(mount.nav().labelKey())) {
            throw BundleRejectedException.invalid(MANIFEST, where + " has a nav item without a 'labelKey'.");
        }
        StarterManifest.Launcher launcher = mount.launcher();
        if (launcher == null) {
            return;
        }
        if (isBlank(launcher.titleKey())) {
            throw BundleRejectedException.invalid(MANIFEST, where + " has a launcher without a 'titleKey'.");
        }
        if (launcher.image() != null && !launcher.image().startsWith(MODULE_ASSET)) {
            throw BundleRejectedException.invalid(MANIFEST, where + ": a launcher image is an asset of the module, '"
                    + MODULE_ASSET + "<path>'.");
        }
    }

    /** Listed means: a contents file, the icon, or the migration script. Nothing else may ride along. */
    private void validateFileList(StarterManifest manifest, Map<String, byte[]> entries) {
        Set<String> contentFiles = new LinkedHashSet<>();
        manifest.contents().values().forEach(contentFiles::addAll);
        for (String path : contentFiles) {
            if (!entries.containsKey(path)) {
                throw BundleRejectedException.invalid(path,
                        MANIFEST + " lists '" + path + "', which the bundle does not contain.");
            }
        }

        Set<String> listed = new LinkedHashSet<>(contentFiles);
        if (manifest.icon() != null) {
            listed.add(manifest.icon());
        }
        if (manifest.migration() != null) {
            listed.add(manifest.migration());
        }
        listed.addAll(manifest.migrations());
        for (String path : entries.keySet()) {
            if (!listed.contains(path)) {
                throw BundleRejectedException.invalid(path, "'" + path + "' is not listed in " + MANIFEST + ".");
            }
        }
    }

    /** A migration file is checked when the bundle is read, so a malformed one fails the packaging, not an organization's upgrade. */
    private void validateMigrations(StarterManifest manifest, Map<String, byte[]> entries) {
        for (String path : manifest.migrations()) {
            try {
                MigrationFile.read(path, entries.get(path));
            } catch (IllegalArgumentException e) {
                throw BundleRejectedException.invalid(path, "'" + path + "': " + e.getMessage() + ".");
            }
        }
    }

    private void verifyIntegrity(StarterManifest manifest, Map<String, byte[]> entries) {
        StarterManifest.Integrity integrity = manifest.integrity();
        if (integrity == null || integrity.files().isEmpty()) {
            return;
        }
        if (integrity.algorithm() != null && !"sha256".equalsIgnoreCase(integrity.algorithm())) {
            throw BundleRejectedException.invalid(MANIFEST,
                    "Unsupported integrity algorithm '" + integrity.algorithm() + "'.");
        }
        integrity.files().forEach((path, hash) -> {
            byte[] content = entries.get(path);
            if (content == null) {
                throw BundleRejectedException.invalid(path,
                        "integrity names '" + path + "', which the bundle does not contain.");
            }
            if (hash == null || !sha256(content).equals(hash.trim().toLowerCase(Locale.ROOT))) {
                throw BundleRejectedException.invalid(path, "'" + path + "' does not match its integrity hash.");
            }
        });
    }

    private void requireSafePath(String path) {
        if (path == null || !SAFE_PATH.matcher(path).matches() || List.of(path.split("/")).contains("..")) {
            throw BundleRejectedException.invalid(path, "Unsafe path in bundle: '" + path + "'.");
        }
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static boolean isBasePath(String path) {
        return path != null && BASE_PATH_CHARACTERS.matcher(path).matches() && !ADJACENT_SEPARATORS.matcher(path).find()
                && Character.isLetterOrDigit(path.charAt(0)) && Character.isLetterOrDigit(path.charAt(path.length() - 1));
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        return Set.copyOf(union);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
