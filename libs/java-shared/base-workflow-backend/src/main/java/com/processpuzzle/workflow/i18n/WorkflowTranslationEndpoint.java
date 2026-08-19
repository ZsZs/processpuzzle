package com.processpuzzle.workflow.i18n;

import com.processpuzzle.workflow.api.BaseWorkflowTranslationsApi;
import com.processpuzzle.core.i18n.AbstractTranslationBundle;
import com.processpuzzle.core.i18n.TranslationBundleKey;
import com.processpuzzle.core.logging.LogClass;
import java.util.Map;
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
 * <p>{@code orgKey} is <em>not</em> verified against the authenticated principal here. That matches this
 * module's existing resources rather than the contract's stated requirement: the access policy lives in
 * base-app, which this module may not depend on. Recorded rather than left implied — a bundle is authored
 * UI text, so the exposure is small, but it is exposure.
 */
@RestController
@LogClass
public class WorkflowTranslationEndpoint implements BaseWorkflowTranslationsApi {

    private final WorkflowTranslationRepository repository;

    public WorkflowTranslationEndpoint(WorkflowTranslationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getWorkflowTranslations(String orgKey, String locale) {
        return bundle(orgKey, AbstractTranslationBundle.ROOT_SCOPE, locale);
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getWorkflowScopedTranslations(String orgKey, String scope, String locale) {
        return bundle(orgKey, scope, locale);
    }

    private ResponseEntity<Map<String, Object>> bundle(String orgKey, String scope, String locale) {
        return ResponseEntity.ok(repository.findById(new TranslationBundleKey(orgKey, scope, locale))
                .map(AbstractTranslationBundle::getMessages)
                .orElseGet(Map::of));
    }
}
