package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.workflow.definition.usecases.inbound.ImportOutcome;
import com.processpuzzle.workflow.definition.usecases.inbound.ImportWorkflowsUseCase;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Seeds bundled default workflow definitions on startup, so a fresh deployment serves
 * ready-to-use workflows. Gated behind {@code base-workflow.loadDefaultWorkflows=true}.
 *
 * <p>Default workflow files are placed in {@code default-workflows/} following the naming
 * convention {@code <orgKey>-workflows.yaml}, e.g. {@code processpuzzle-testbed-workflows.yaml}.
 * The pattern is {@code classpath*:} so that a host application can contribute its own
 * {@code default-workflows/} directory alongside this library's.
 *
 * <p>Nothing here can fail startup: every problem is logged and the next file or definition is attempted.
 */
@Component
@ConditionalOnProperty(prefix = "base-workflow", name = "loadDefaultWorkflows", havingValue = "true")
public class DefaultWorkflowImporter {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultWorkflowImporter.class);
    private static final String WORKFLOWS_FILE_SUFFIX = "-workflows.yaml";
    private static final String DEFAULT_WORKFLOWS_LOCATION = "classpath*:default-workflows/*" + WORKFLOWS_FILE_SUFFIX;

    private final ImportWorkflowsUseCase importWorkflows;
    private final ResourcePatternResolver resourceResolver;

    public DefaultWorkflowImporter(ImportWorkflowsUseCase importWorkflows,
                                  ResourcePatternResolver resourceResolver) {
        this.importWorkflows = importWorkflows;
        this.resourceResolver = resourceResolver;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadDefaults() {
        Resource[] resources;
        try {
            resources = resourceResolver.getResources(DEFAULT_WORKFLOWS_LOCATION);
        } catch (IOException e) {
            LOG.warn("Unable to scan for default workflow files at {}", DEFAULT_WORKFLOWS_LOCATION, e);
            return;
        }

        if (resources.length == 0) {
            LOG.info("No default workflow files found at {}", DEFAULT_WORKFLOWS_LOCATION);
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
            LOG.warn("Skipping default workflow file '{}': the name does not follow the '<orgKey>{}' convention.",
                    fileName, WORKFLOWS_FILE_SUFFIX);
            return;
        }

        try (InputStream input = resource.getInputStream()) {
            ImportOutcome outcome = importWorkflows.execute(orgKey, input);
            LOG.info("Imported default workflows from {} into organization '{}': created={}, updated={}, errors={}",
                    fileName, orgKey, outcome.created(), outcome.updated(), outcome.errors() == null ? 0 : outcome.errors().size());
            if (outcome.errors() != null) {
                outcome.errors().forEach(error -> LOG.warn("Default workflow import error in {}: {}", fileName, error));
            }
        } catch (Exception e) {
            LOG.warn("Failed to import default workflows from {}", fileName, e);
        }
    }

    private String orgKeyOf(String fileName) {
        if (fileName == null || !fileName.endsWith(WORKFLOWS_FILE_SUFFIX)) {
            return null;
        }
        String orgKey = fileName.substring(0, fileName.length() - WORKFLOWS_FILE_SUFFIX.length());
        return orgKey.isBlank() ? null : orgKey;
    }
}
