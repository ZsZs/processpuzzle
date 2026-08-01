package com.processpuzzle.app.usecase.service;

import java.util.Set;

/**
 * The {@code --pp-*} CSS custom properties a theme may override.
 *
 * <p>These names are duplicated from {@code libs/js-shared/widgets/src/theme/pp-colors.css}, which
 * is the real source of truth — the tokens are a frontend concern and the backend has no way to
 * read that file at run-time. Keep the two in sync when adding a token; the only consequence of
 * drift is that a newly added token is rejected by validation until it is listed here.
 */
public final class PpThemeTokens {

    private static final Set<String> NAMES = Set.of(
            // base palette
            "--pp-color-white",
            "--pp-color-light-green",
            "--pp-color-light-blue",
            "--pp-color-dark-blue",
            // semantic surfaces
            "--pp-surface-base",
            "--pp-surface-header",
            "--pp-surface-card",
            "--pp-surface-sidenav",
            "--pp-on-sidenav",
            // buttons
            "--pp-button-primary-bg",
            "--pp-button-primary-text",
            "--pp-button-secondary-bg",
            "--pp-button-secondary-text",
            "--pp-button-delete-bg",
            "--pp-button-delete-text",
            // chips
            "--pp-chip-bg",
            "--pp-chip-text");

    private PpThemeTokens() {
    }

    public static boolean isKnown(String tokenName) {
        return tokenName != null && NAMES.contains(tokenName);
    }

    public static Set<String> names() {
        return NAMES;
    }
}
