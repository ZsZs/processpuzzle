package com.processpuzzle.app.usecase.service;

import com.processpuzzle.app.domain.AppRoute;
import com.processpuzzle.app.domain.ModuleDefinition;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What of a module is translatable, and under which keys.
 *
 * <p>The module's name and each route's title, keyed relative to the module's transloco scope — as in
 * {@code assets/i18n/<scope>/<lang>.json}, since transloco prefixes them with the scope alias itself. An
 * element that names a {@code translocoId} inside the scope is keyed by it; one that names none gets a
 * derived key, so a designer never has to type one:
 *
 * <ul>
 *   <li>the module name: {@code module.name}
 *   <li>a route title: {@code route.<path>}, the path verbatim (for example {@code route.orders/:id})
 * </ul>
 *
 * The derived keys are what the seeded modules already spell out ({@code order_admin.route.orders} in scope
 * {@code order_admin}). A {@code translocoId} <em>outside</em> the scope belongs to somebody else's
 * translations and is left out. The frontend applies the same rule when it renders the titles.
 */
public final class ModuleTranslationKeys {

    private ModuleTranslationKeys() {
    }

    /** The scope a module's translations load under: its own, else its key. */
    public static String scopeOf(ModuleDefinition module) {
        String scope = module.getTranslocoScope();
        return scope == null || scope.isBlank() ? module.getKey() : scope.trim();
    }

    /** Relative key to authored text, in authoring order. The first element to claim a key keeps it. */
    public static Map<String, String> of(ModuleDefinition module) {
        String scope = scopeOf(module);
        Map<String, String> texts = new LinkedHashMap<>();
        add(texts, relativeKey(module.getTranslocoId(), scope, "module.name"), module.getName());
        if (module.getRoutes() != null) {
            for (AppRoute route : module.getRoutes()) {
                add(texts, relativeKey(route.translocoId(), scope, "route." + route.path()), route.title());
            }
        }
        return texts;
    }

    private static void add(Map<String, String> texts, String key, String text) {
        if (key != null && text != null && !text.isBlank()) {
            texts.putIfAbsent(key, text);
        }
    }

    private static String relativeKey(String translocoId, String scope, String derived) {
        if (translocoId == null || translocoId.isBlank()) {
            return derived;
        }
        String prefix = scope + ".";
        return translocoId.startsWith(prefix) && translocoId.length() > prefix.length()
                ? translocoId.substring(prefix.length())
                : null;
    }
}
