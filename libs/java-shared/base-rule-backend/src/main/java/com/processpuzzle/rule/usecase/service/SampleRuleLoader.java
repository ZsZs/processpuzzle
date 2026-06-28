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

@Component
@ConditionalOnProperty(prefix = "base-rule", name = "loadSamples", havingValue = "true")
public class SampleRuleLoader {
    private static final Logger LOG = LoggerFactory.getLogger(SampleRuleLoader.class);
    private static final String SAMPLE_RULES_LOCATION = "classpath:sample-rules/*.yaml";
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
        try (InputStream input = resource.getInputStream()) {
            ImportOutcome outcome = importRules.execute(input);
            LOG.info("Imported sample rules from {}: created={}, updated={}, errors={}",
                    name, outcome.created(), outcome.updated(), outcome.errors().size());
            outcome.errors().forEach(error -> LOG.warn("Sample rule import error in {}: {}", name, error));
        } catch (IOException e) {
            LOG.warn("Failed to import sample rules from {}", name, e);
        }
    }
}
