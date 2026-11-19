package com.processpuzzle.app.domain;

import java.util.List;

/**
 * Mounts a {@link ModuleDefinition}'s routes into an application under {@code basePath}.
 *
 * <p>The one place route structure composes, and it composes exactly one level deep, because a
 * module cannot mount modules. That bound is what keeps an application's routing a short flat list
 * plus a handful of mounts rather than an arbitrarily deep tree.
 *
 * @param moduleKey {@code ModuleDefinition.key} within this organization; a key naming no existing
 *                  module is a validation warning, not an error — modules are loosely coupled, so
 *                  an app may reference one not yet authored
 * @param basePath path prefix every route of the module is registered under
 * @param launcher the card the application's launcher draws for this module, or {@code null} for none; set by a domain
 *                 starter's mount hint, and the customer's to change or clear
 */
public record ModuleMount(String moduleKey, String basePath, Launcher launcher) {

    /** A mount without a launcher card. */
    public ModuleMount(String moduleKey, String basePath) {
        this(moduleKey, basePath, null);
    }

    /**
     * What the launcher draws for a mount. Texts are keys of the module's own translations, relative to its scope;
     * {@code image} is an asset of the module ({@code module:<path>}); {@code link} is the module route the card
     * opens, relative to the mount, and the module's first route when null.
     */
    public record Launcher(String image, String titleKey, String subtitleKey, List<String> contentKeys, String link) {

        public Launcher {
            contentKeys = contentKeys == null ? List.of() : List.copyOf(contentKeys);
        }
    }
}
