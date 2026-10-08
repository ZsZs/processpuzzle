package com.processpuzzle.event.adapter.inbound;

import com.processpuzzle.event.usecase.ImportEventDefinitions;
import com.processpuzzle.event.usecase.ImportOutcome;
import java.io.IOException;
import java.io.InputStream;
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
 * Seeds the bundled event definitions on startup, gated behind
 * {@code base-event.loadDefaultEventDefinitions=true}. Files follow
 * {@code default-event-definitions/base-event/<orgKey>-event-definitions.yaml}: the feature directory
 * keeps a {@code classpath*:} scan from also picking up another library's same-named seed file, and
 * a host application can still contribute its own files under the same directory.
 *
 * <p>The import upserts, so editing a seed file reaches a stack that has already been seeded. Nothing
 * here fails startup: every problem is logged and the next file is attempted.
 */
@Component
@ConditionalOnProperty(prefix = "base-event", name = "loadDefaultEventDefinitions", havingValue = "true")
public class DefaultEventDefinitionImporter {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultEventDefinitionImporter.class);
    private static final String FILE_SUFFIX = "-event-definitions.yaml";
    static final String LOCATION = "classpath*:default-event-definitions/base-event/*" + FILE_SUFFIX;

    private final ImportEventDefinitions importEventDefinitions;
    private final ResourcePatternResolver resourceResolver;

    public DefaultEventDefinitionImporter(ImportEventDefinitions importEventDefinitions,
                                          ResourcePatternResolver resourceResolver) {
        this.importEventDefinitions = importEventDefinitions;
        this.resourceResolver = resourceResolver;
    }

    /**
     * Ahead of base-workflow's seed ({@code DefaultWorkflowImporter.SEED_ORDER}, 100): a workflow with a
     * TRIGGERING_EVENT start event is refused at import when its event is not yet in the catalog, and
     * the workflow importer logs that rather than failing startup — so the wrong order would be silent.
     * The number is repeated rather than referenced because this module does not depend on base-workflow.
     */
    @Order(50)
    @EventListener(ApplicationReadyEvent.class)
    public void loadDefaults() {
        Resource[] resources;
        try {
            resources = resourceResolver.getResources(LOCATION);
        } catch (IOException e) {
            LOG.warn("Unable to scan for default event definition files at {}", LOCATION, e);
            return;
        }
        if (resources.length == 0) {
            LOG.info("No default event definition files found at {}", LOCATION);
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
            LOG.warn("Skipping default event definition file '{}': the name does not follow the '<orgKey>{}' convention.",
                    fileName, FILE_SUFFIX);
            return;
        }
        try (InputStream input = resource.getInputStream()) {
            ImportOutcome outcome = importEventDefinitions.execute(orgKey, input);
            LOG.info("Imported default event definitions from {} into organization '{}': created={}, updated={}, errors={}",
                    fileName, orgKey, outcome.created(), outcome.updated(), outcome.errors().size());
            outcome.errors().forEach(error -> LOG.warn("Default event definition import error in {}: {}", fileName, error));
        } catch (Exception e) {
            LOG.warn("Failed to import default event definitions from {}", fileName, e);
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
