package com.processpuzzle.state.adapter.inbound;

import com.processpuzzle.state.usecase.ImportOutcome;
import com.processpuzzle.state.usecase.ImportStateMachineDefinitions;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.annotation.Order;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Seeds the bundled default state machine definitions on startup, so a fresh deployment serves
 * ready-to-use lifecycle state machines for entities (such as Dynamic Entity). Gated behind
 * {@code base-state.loadDefaultStateMachines=true}.
 *
 * <p>Default state machine files are placed in {@code default-state-machines/} following the naming
 * convention {@code <orgKey>-state-machines.yaml}, e.g. {@code processpuzzle-testbed-state-machines.yaml}.
 * The pattern is {@code classpath*:} so that a host application can contribute its own
 * {@code default-state-machines/} directory alongside this library's.
 *
 * <p>Nothing here can fail startup: every problem is logged and the next file or definition is attempted.
 */
@Component
@ConditionalOnProperty(prefix = "base-state", name = "loadDefaultStateMachines", havingValue = "true")
public class DefaultStateImporter {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultStateImporter.class);
    private static final String STATE_MACHINES_FILE_SUFFIX = "-state-machines.yaml";
    private static final String DEFAULT_STATE_MACHINES_LOCATION = "classpath*:default-state-machines/*" + STATE_MACHINES_FILE_SUFFIX;

    private final ImportStateMachineDefinitions importStateMachineDefinitions;
    private final ResourcePatternResolver resourceResolver;

    public DefaultStateImporter(ImportStateMachineDefinitions importStateMachineDefinitions,
                                ResourcePatternResolver resourceResolver) {
        this.importStateMachineDefinitions = importStateMachineDefinitions;
        this.resourceResolver = resourceResolver;
    }

    /**
     * Ordered after base-entity's {@code DefaultEntityLoader} ({@code @Order(10)}): every machine
     * imported here names an entity definition and an attribute of it, and the validator rejects a
     * machine whose entity type base-entity does not yet know. Since this importer logs its errors
     * rather than failing startup, getting the order wrong would be silent.
     */
    @Order(20)
    @EventListener(ApplicationReadyEvent.class)
    public void loadDefaults() {
        Resource[] resources;
        try {
            resources = resourceResolver.getResources(DEFAULT_STATE_MACHINES_LOCATION);
        } catch (IOException e) {
            LOG.warn("Unable to scan for default state machine files at {}", DEFAULT_STATE_MACHINES_LOCATION, e);
            return;
        }

        if (resources.length == 0) {
            LOG.info("No default state machine files found at {}", DEFAULT_STATE_MACHINES_LOCATION);
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
            LOG.warn("Skipping default state machine file '{}': the name does not follow the '<orgKey>{}' convention.",
                    fileName, STATE_MACHINES_FILE_SUFFIX);
            return;
        }

        try (InputStream input = resource.getInputStream()) {
            ImportOutcome outcome = importStateMachineDefinitions.execute(orgKey, input);
            LOG.info("Imported default state machines from {} into organization '{}': created={}, updated={}, errors={}",
                    fileName, orgKey, outcome.created(), outcome.updated(), outcome.errors().size());
            if (outcome.errors() != null) {
                outcome.errors().forEach(error -> LOG.warn("Default state machine import error in {}: {}", fileName, error));
            }
        } catch (Exception e) {
            LOG.warn("Failed to import default state machines from {}", fileName, e);
        }
    }

    private String orgKeyOf(String fileName) {
        if (fileName == null || !fileName.endsWith(STATE_MACHINES_FILE_SUFFIX)) {
            return null;
        }
        String orgKey = fileName.substring(0, fileName.length() - STATE_MACHINES_FILE_SUFFIX.length());
        return orgKey.isBlank() ? null : orgKey;
    }
}
