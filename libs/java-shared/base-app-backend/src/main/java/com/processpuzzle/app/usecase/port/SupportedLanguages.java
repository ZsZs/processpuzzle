package com.processpuzzle.app.usecase.port;

import java.util.List;

/**
 * Outbound port answering which languages an organization's applications are offered in — the languages a
 * module's translation files are prepared for.
 *
 * <p>The list is deployment configuration ({@code AVAILABLE_LANGUAGES} of the frontend's run-time
 * configuration), which this library cannot read, so the host application states it. A per-organization
 * setting could answer the same question later without any change here.
 *
 * <p>Unimplemented, the port names no language. base-app then prepares the organization's default language
 * alone and accepts an upload in any well-formed language tag, rather than refusing everything — the same
 * direction as {@link TenantDirectory}.
 */
public interface SupportedLanguages {

    /** BCP-47 tags of the supported languages, in the order they are offered; empty when the host names none. */
    default List<String> codes(String orgKey) {
        return List.of();
    }
}
