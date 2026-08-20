package com.processpuzzle.app.i18n;

import com.processpuzzle.app.api.BaseAppTranslationsApi;
import com.processpuzzle.core.i18n.AbstractTranslationBundle;
import com.processpuzzle.core.i18n.TranslationBundleResponseProvider;
import com.processpuzzle.core.logging.LogClass;
import java.util.Map;
import com.processpuzzle.app.usecase.OrganizationGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves this feature's Transloco bundles, in the shape the frontend's {@code assets/i18n} files have.
 *
 * <p>The frontend reaches here only after an asset miss, which is the case that has no static file to
 * begin with: a designer-authored module names its scope at run-time, so no build could have shipped
 * {@code assets/i18n/<scope>/<lang>.json} for it.
 *
 * <p><strong>An unseeded bundle is 200 with an empty object, not 404.</strong> The loader cannot act on
 * the difference — it falls back to an empty bundle either way — and a 404 per unseeded scope would fill
 * the browser console with errors that mean nothing is wrong. It would also make a genuinely broken
 * backend indistinguishable from a locale nobody has translated yet.
 *
 * <p>{@code orgKey} is checked against the authenticated principal through {@link OrganizationGuard}, as
 * every base-app resource is. The other features serve their bundles unguarded, matching the posture of
 * their own existing resources — they have no organization registry to consult, their
 * {@code allowedDependencies} naming only {@code core} and {@code shared}.
 */
@RestController
@LogClass
public class AppTranslationEndpoint implements BaseAppTranslationsApi {

    private final TranslationBundleResponseProvider<AppTranslationBundle> responseProvider;
    private final OrganizationGuard organizationGuard;

    public AppTranslationEndpoint(AppTranslationRepository repository, OrganizationGuard organizationGuard) {
        responseProvider = new TranslationBundleResponseProvider<>(repository::findById);
        this.organizationGuard = organizationGuard;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getAppTranslations(String orgKey, String locale) {
        return bundle(orgKey, AbstractTranslationBundle.ROOT_SCOPE, locale);
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getAppScopedTranslations(String orgKey, String scope, String locale) {
        return bundle(orgKey, scope, locale);
    }

    private ResponseEntity<Map<String, Object>> bundle(String orgKey, String scope, String locale) {
        organizationGuard.requireAccess(orgKey);
        return responseProvider.bundle(orgKey, scope, locale);
    }
}
