package com.processpuzzle.app.usecase.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Organization keys that cannot be claimed because they would collide with the platform's own
 * top-level routes.
 *
 * <p>An organization key becomes a path segment of the public site
 * ({@code https://processpuzzle.com/{orgKey}}), so it shares a namespace with the application's
 * own URLs. A tenant called {@code api} would shadow the REST API.
 *
 * <p>Additional keys can be reserved per deployment through
 * {@code base-app.reserved-organization-keys}, following how {@code base-rule.loadSamples} is
 * configured in the application yaml.
 */
@Component
public class ReservedOrganizationKeys {

    private static final Set<String> DEFAULTS = Set.of(
            "admin", "api", "assets", "auth", "callback", "design", "docs", "health", "help",
            "images", "login", "logout", "oauth2", "public", "signup", "static", "status",
            "support", "swagger", "well-known", "www");

    private final Set<String> reserved;

    public ReservedOrganizationKeys(
            @Value("${base-app.reserved-organization-keys:}") List<String> additional) {
        Set<String> all = new LinkedHashSet<>(DEFAULTS);
        if (additional != null) {
            additional.stream()
                    .filter(key -> key != null && !key.isBlank())
                    .map(key -> key.trim().toLowerCase(Locale.ROOT))
                    .forEach(all::add);
        }
        this.reserved = Collections.unmodifiableSet(all);
    }

    public boolean isReserved(String orgKey) {
        return orgKey != null && reserved.contains(orgKey.trim().toLowerCase(Locale.ROOT));
    }

    public Set<String> all() {
        return reserved;
    }
}
