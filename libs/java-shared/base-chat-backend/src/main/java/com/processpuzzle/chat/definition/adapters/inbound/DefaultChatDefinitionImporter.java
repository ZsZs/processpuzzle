package com.processpuzzle.chat.definition.adapters.inbound;

import com.processpuzzle.chat.definition.usecases.inbound.ImportChatDefinitions;
import com.processpuzzle.chat.definition.usecases.inbound.ImportOutcome;
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
 * Seeds the bundled chat definitions on startup, gated behind {@code base-chat.loadDefaultChatDefinitions=true}.
 * Files follow {@code default-chat-definitions/base-chat/<orgKey>-chat-definitions.yaml}: the feature
 * directory keeps a {@code classpath*:} scan from also picking up another library's same-named seed file,
 * and a host application can still contribute its own files under the same directory.
 *
 * <p>The import upserts and releases what the file wrote, so editing a seed file reaches a stack that has
 * already been seeded. Nothing here fails startup: every problem is logged and the next file is attempted.
 */
@Component
@ConditionalOnProperty(prefix = "base-chat", name = "loadDefaultChatDefinitions", havingValue = "true")
public class DefaultChatDefinitionImporter {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultChatDefinitionImporter.class);
    private static final String FILE_SUFFIX = "-chat-definitions.yaml";
    static final String LOCATION = "classpath*:default-chat-definitions/base-chat/*" + FILE_SUFFIX;

    private final ImportChatDefinitions importChatDefinitions;
    private final ResourcePatternResolver resourceResolver;

    public DefaultChatDefinitionImporter(ImportChatDefinitions importChatDefinitions, ResourcePatternResolver resourceResolver) {
        this.importChatDefinitions = importChatDefinitions;
        this.resourceResolver = resourceResolver;
    }

    @Order(60)
    @EventListener(ApplicationReadyEvent.class)
    public void loadDefaults() {
        Resource[] resources;
        try {
            resources = resourceResolver.getResources(LOCATION);
        } catch (IOException e) {
            LOG.warn("Unable to scan for default chat definition files at {}", LOCATION, e);
            return;
        }
        if (resources.length == 0) {
            LOG.info("No default chat definition files found at {}", LOCATION);
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
            LOG.warn("Skipping default chat definition file '{}': the name does not follow the '<orgKey>{}' convention.",
                    fileName, FILE_SUFFIX);
            return;
        }
        try (InputStream input = resource.getInputStream()) {
            ImportOutcome outcome = importChatDefinitions.execute(orgKey, input, true);
            LOG.info("Imported default chat definitions from {} into organization '{}': created={}, updated={}, errors={}",
                    fileName, orgKey, outcome.created(), outcome.updated(), outcome.errors().size());
            outcome.errors().forEach(error -> LOG.warn("Default chat definition import error in {}: {}", fileName, error));
        } catch (Exception e) {
            LOG.warn("Failed to import default chat definitions from {}", fileName, e);
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
