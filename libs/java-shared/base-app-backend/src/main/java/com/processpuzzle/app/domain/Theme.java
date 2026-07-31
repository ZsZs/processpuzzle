package com.processpuzzle.app.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The tenant's look, as persisted inside {@link AppGraph}.
 *
 * <p>{@code materialTheme} and {@code colorScheme} are plain strings rather than enums on
 * purpose: this record is serialized into the {@code app_graph} JSON column, and a blob
 * holding an enum constant that a later release removes would be unreadable. The allowed
 * values are checked by {@code AppDefinitionValidator} instead.
 *
 * @param materialTheme prebuilt Angular Material theme name, e.g. {@code azure-blue}
 * @param colorScheme {@code light}, {@code dark} or {@code auto}
 * @param tokenOverrides overrides for the {@code --pp-*} CSS custom properties, keyed with the leading {@code --}
 * @param logoUrl tenant logo shown in the header region
 * @param faviconUrl tenant favicon
 */
public record Theme(
        String materialTheme,
        String colorScheme,
        Map<String, String> tokenOverrides,
        String logoUrl,
        String faviconUrl) {

    public Theme {
        tokenOverrides = tokenOverrides == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(tokenOverrides));
    }
}
