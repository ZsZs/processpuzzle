package com.processpuzzle.core.definition;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Mounts the modules of an installed domain starter into the organization's application, and takes them out again.
 * Declared here, implemented by {@code base-app}, called by {@code base-starter-backend}: the starter feature knows
 * nothing of applications, and the application nothing of starters (business-starters design §11.3).
 *
 * <p>Both calls write through the transaction they are called in and must not commit it, as a
 * {@link DefinitionImportParticipant} must not, so a dry run that rolls the install back rolls the mounts back too.
 * What is written is the application's <em>draft</em>; whether it is released at once is the implementation's rule
 * (business-starters design, decision 11a), and {@link MountOutcome#released()} says what it did.
 */
public interface ModuleMountPort {

    /**
     * Mounts the modules the hints name — modules the same install has just written — into the organization's
     * application: a mount, a sidenav entry and the launcher card the hint describes. A hint that cannot be honoured
     * (no application, the module is mounted already, the path is taken) is skipped and reported, never an error:
     * the modules are installed either way and the customer can mount them by hand.
     */
    MountOutcome mount(String orgKey, List<MountHint> hints);

    /** Removes the mounts of {@code moduleKeys}, with the sidenav entries made for them; keys not mounted are ignored. */
    MountOutcome unmount(String orgKey, Collection<String> moduleKeys);

    /**
     * A manifest's {@code mounts} entry.
     *
     * @param basePath where the module's routes go, a suggestion: a taken path skips the mount
     * @param nav      the sidenav entry; null for none
     * @param launcher the card of the application's launcher; null for none
     */
    record MountHint(String moduleKey, String basePath, Nav nav, Launcher launcher) {
    }

    /** @param labelKey a text of the module's own translations, relative to its scope */
    record Nav(String icon, String labelKey) {
    }

    /**
     * @param image       an asset of the module, {@code module:<path>}
     * @param titleKey    texts of the module's own translations, relative to its scope
     * @param link        the module route the card opens, relative to the mount; the module's first route when null
     */
    record Launcher(String image, String titleKey, String subtitleKey, List<String> contentKeys, String link) {

        public Launcher {
            contentKeys = contentKeys == null ? List.of() : List.copyOf(contentKeys);
        }
    }

    /**
     * What a call did.
     *
     * @param done     the module keys mounted, or unmounted
     * @param skipped  the module keys it did not touch, with the reason
     * @param released whether the application was released as well, because it was still the untouched default shell
     */
    record MountOutcome(List<String> done, Map<String, String> skipped, boolean released) {

        public MountOutcome {
            done = done == null ? List.of() : List.copyOf(done);
            skipped = skipped == null ? Map.of() : Map.copyOf(skipped);
        }

        public static MountOutcome nothing() {
            return new MountOutcome(List.of(), Map.of(), false);
        }
    }
}
