package com.processpuzzle.calculation.adapter.inbound;

import com.processpuzzle.calculation.usecase.ImportCalculationDefinitions;
import com.processpuzzle.calculation.usecase.ImportOutcome;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Seeds the bundled calculations on startup, gated behind {@code base-calculation.loadDefaultCalculations=true}.
 * Files follow {@code default-calculations/base-calculation/<orgKey>-calculations.yaml}, one calculation per YAML
 * document. The import upserts, and what it wrote is released at once: a seed is a vetted set.
 *
 * <p>Nothing here fails startup: every problem is logged and the next file is attempted.
 */
@Component
@ConditionalOnProperty(prefix = "base-calculation", name = "loadDefaultCalculations", havingValue = "true")
public class DefaultCalculationImporter {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultCalculationImporter.class);
    private static final String FILE_SUFFIX = "-calculations.yaml";
    static final String LOCATION = "classpath*:default-calculations/base-calculation/*" + FILE_SUFFIX;

    private final ImportCalculationDefinitions importer;
    private final ResourcePatternResolver resourceResolver;
    private final CalculationReleaseParticipant releaseParticipant;

    public DefaultCalculationImporter(ImportCalculationDefinitions importer, ResourcePatternResolver resourceResolver,
                                      CalculationReleaseParticipant releaseParticipant) {
        this.importer = importer;
        this.resourceResolver = resourceResolver;
        this.releaseParticipant = releaseParticipant;
    }

    /** After the entity seed, whose definitions a calculation names, and before the workflows. */
    @Order(60)
    @EventListener(ApplicationReadyEvent.class)
    public void loadDefaults() {
        Resource[] resources;
        try {
            resources = resourceResolver.getResources(LOCATION);
        } catch (IOException e) {
            LOG.warn("Unable to scan for default calculation files at {}", LOCATION, e);
            return;
        }
        if (resources.length == 0) {
            LOG.info("No default calculation files found at {}", LOCATION);
            return;
        }
        for (Resource resource : resources) {
            load(resource);
        }
    }

    private void load(Resource resource) {
        String fileName = resource.getFilename();
        String orgKey = orgKeyOf(fileName);
        if (orgKey == null) {
            LOG.warn("Skipping default calculation file '{}': the name does not follow the '<orgKey>{}' convention.",
                    fileName, FILE_SUFFIX);
            return;
        }
        try (InputStream input = resource.getInputStream()) {
            ImportOutcome outcome = importer.execute(orgKey, new String(input.readAllBytes(), StandardCharsets.UTF_8));
            releaseParticipant.release(orgKey, outcome.written());
            LOG.info("Imported default calculations from {} into organization '{}': created={}, updated={}, errors={}",
                    fileName, orgKey, outcome.created(), outcome.updated(), outcome.errors().size());
            outcome.errors().forEach(error -> LOG.warn("Default calculation import error in {}: {}", fileName, error));
        } catch (Exception e) {
            LOG.warn("Failed to import default calculations from {}", fileName, e);
        }
    }

    static String orgKeyOf(String fileName) {
        if (fileName == null || !fileName.endsWith(FILE_SUFFIX)) {
            return null;
        }
        String orgKey = fileName.substring(0, fileName.length() - FILE_SUFFIX.length());
        return orgKey.isBlank() ? null : orgKey;
    }
}
