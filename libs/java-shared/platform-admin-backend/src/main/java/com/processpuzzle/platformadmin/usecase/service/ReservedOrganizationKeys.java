package com.processpuzzle.platformadmin.usecase.service;

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
 * <p>Two entries in the list below are load-bearing beyond mere collision avoidance.
 * {@code admin} is the segment {@code org-admin-frontend} mounts on inside a tenant's own space
 * ({@code /{orgKey}/admin/users}), and {@code platform} is the namespace the staff API and the
 * {@code platform-admin} application claim. Both were already unclaimable — {@code admin} by the
 * original list, {@code platform} added when the staff surface was introduced — which is why
 * neither feature had to negotiate for its path.
 *
 * <p>The two application-stack keys are here rather than in the per-deployment property below,
 * because {@code processpuzzle-testbed} and {@code processpuzzle-admin} name platform-owned stacks in
 * every deployment — see docs/application-stacks.md. Their realm name, organization key and MinIO
 * bucket prefix are one string, so a customer allowed to claim either key would be claiming a realm
 * and a bucket namespace the platform already owns.
 *
 * <p><strong>Except this deployment's own stack key.</strong> One backend serves one stack, and that
 * stack's organization has to be created before anything in it can be: {@code DefaultAppLoader}
 * bootstraps it through {@code checkOrganizationKey} / {@code provisionOrganization} like any other
 * claim, and treats a reserved key as unclaimable. Reserving the key a deployment serves therefore
 * stops that deployment from seeding its own organization — its default apps are skipped and every
 * later {@code createAppDefinition} in it answers {@code OrganizationNotFoundException}. So the key
 * named by {@code platform-admin.stack-organization-key} (defaulting to the realm this deployment
 * trusts) is removed from the set, while every <em>other</em> stack's key stays reserved. The
 * protection survives: after bootstrap the key answers {@code organization.key.taken}, and bootstrap
 * runs before the first request is served.
 *
 * <p>Additional keys can be reserved per deployment through
 * {@code platform-admin.reserved-organization-keys}, following how {@code base-rule.loadSamples} is
 * configured in the application yaml. The prefix moved with the aggregate from
 * {@code base-app.*}; nothing in the repo set the old name, so no deployment config had to change.
 */
@Component
public class ReservedOrganizationKeys {

    private static final Set<String> DEFAULTS = Set.of(
            "admin", "api", "assets", "auth", "callback", "design", "docs", "health", "help",
            "images", "login", "logout", "oauth2", "platform", "public", "signup", "static",
            "status", "support", "swagger", "well-known", "www",
            // The platform's own application stacks.
            "processpuzzle-testbed", "processpuzzle-admin");

    private final Set<String> reserved;

    public ReservedOrganizationKeys(
            @Value("${platform-admin.reserved-organization-keys:}") List<String> additional,
            @Value("${platform-admin.stack-organization-key:${processpuzzle.security.stack-realm:}}")
            String ownStackKey) {
        Set<String> all = new LinkedHashSet<>(DEFAULTS);
        if (additional != null) {
            additional.stream()
                    .filter(key -> key != null && !key.isBlank())
                    .map(key -> key.trim().toLowerCase(Locale.ROOT))
                    .forEach(all::add);
        }
        // Last, so it also wins over an explicit reserved-organization-keys entry. A deployment that
        // reserves the key it serves has only disabled its own bootstrap, which is never the intent.
        if (ownStackKey != null && !ownStackKey.isBlank()) {
            all.remove(ownStackKey.trim().toLowerCase(Locale.ROOT));
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
