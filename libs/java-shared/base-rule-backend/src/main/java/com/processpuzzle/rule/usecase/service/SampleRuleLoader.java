package com.processpuzzle.rule.usecase.service;

import com.processpuzzle.rule.usecase.ImportOutcome;
import com.processpuzzle.rule.usecase.ImportRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Development convenience: imports the bundled sample rules on startup. Gated behind
 * {@code base-rule.loadSamples=true}.
 *
 * <p>Rules are tenant-scoped, so every sample has to belong to <em>some</em> organization.
 * The owning organization is part of the file name: {@code <orgKey>-rules.yaml} lands in
 * {@code orgKey}, so {@code processpuzzle-testbed-rules.yaml} is imported into
 * {@code processpuzzle-testbed} and {@code processpuzzle-rules.yaml} into
 * {@code processpuzzle}. One deployment can therefore seed several organizations, and adding
 * one is adding a file — no configuration change.
 */
@Component
@ConditionalOnProperty(prefix = "base-rule", name = "loadSamples", havingValue = "true")
public class SampleRuleLoader {
    private static final Logger LOG = LoggerFactory.getLogger(SampleRuleLoader.class);
    private static final String RULES_FILE_SUFFIX = "-rules.yaml";
    private static final String SAMPLE_RULES_LOCATION = "classpath:sample-rules/*" + RULES_FILE_SUFFIX;
    private final ImportRules importRules;
    private final ResourcePatternResolver resourceResolver;

    public SampleRuleLoader(ImportRules importRules, ResourcePatternResolver resourceResolver) {
        this.importRules = importRules;
        this.resourceResolver = resourceResolver;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadSamples() {
        Resource[] resources;
        try {
            resources = resourceResolver.getResources(SAMPLE_RULES_LOCATION);
        } catch (IOException e) {
            LOG.warn("Unable to scan for sample rule files at {}", SAMPLE_RULES_LOCATION, e);
            return;
        }

        if (resources.length == 0) {
            LOG.info("No sample rule files found at {}", SAMPLE_RULES_LOCATION);
            return;
        }

        for (Resource resource : resources) {
            importSample(resource);
        }
    }

    private void importSample(Resource resource) {
        String name = resource.getFilename();
        String orgKey = orgKeyOf(name);
        if (orgKey == null) {
            LOG.warn("Skipping sample rule file '{}': the name does not follow the '<orgKey>{}' convention.",
                    name, RULES_FILE_SUFFIX);
            return;
        }
        try (InputStream input = resource.getInputStream()) {
            ImportOutcome outcome = importRules.execute(orgKey, input);
            LOG.info("Imported sample rules from {} into organization '{}': created={}, updated={}, errors={}",
                    name, orgKey, outcome.created(), outcome.updated(), outcome.errors().size());
            outcome.errors().forEach(error -> LOG.warn("Sample rule import error in {}: {}", name, error));
        } catch (IOException e) {
            LOG.warn("Failed to import sample rules from {}", name, e);
        }
    }

    /** The part of {@code <orgKey>-rules.yaml} before the suffix, or {@code null} if there is none. */
    private String orgKeyOf(String filename) {
        if (filename == null || !filename.endsWith(RULES_FILE_SUFFIX)) {
            return null;
        }
        String orgKey = filename.substring(0, filename.length() - RULES_FILE_SUFFIX.length());
        return orgKey.isBlank() ? null : orgKey;
    }
}
