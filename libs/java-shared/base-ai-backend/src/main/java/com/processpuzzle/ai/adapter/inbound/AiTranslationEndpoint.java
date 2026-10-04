package com.processpuzzle.ai.adapter.inbound;

import com.processpuzzle.ai.api.AiTranslationsApi;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * This feature's UI message bundles. It ships none yet, so every bundle is empty — which the contract
 * defines as the answer for a scope without keys, and which keeps the frontend's loader from failing.
 */
@RestController
public class AiTranslationEndpoint implements AiTranslationsApi {

    @Override
    public ResponseEntity<Map<String, Object>> getAiTranslations(String orgKey, String locale) {
        return ResponseEntity.ok(Map.of());
    }

    @Override
    public ResponseEntity<Map<String, Object>> getAiScopedTranslations(String orgKey, String scope, String locale) {
        return ResponseEntity.ok(Map.of());
    }
}
