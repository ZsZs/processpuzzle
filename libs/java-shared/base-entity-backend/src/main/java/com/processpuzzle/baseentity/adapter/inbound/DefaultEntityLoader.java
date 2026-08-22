package com.processpuzzle.baseentity.adapter.inbound;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.baseentity.adapter.inbound.dto.DefaultEntitiesDocument;
import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.definition.adapters.inbound.EntityDefinitionMapper;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.usecases.inbound.CreateEntityDefinitionUseCase;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.usecases.inbound.CreateEntityInstanceUseCase;
import com.processpuzzle.baseentity.model.BaseEntityDefinitionInput;
import com.processpuzzle.baseentity.model.EntityObjectInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.annotation.Order;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Seeds the bundled default entity definitions and sample entity instances on startup, so a fresh
 * deployment serves ready-to-use entity types and instances. Gated behind
 * {@code base-entity.loadDefaultEntities=true}.
 *
 * <p>Default entity files are placed in {@code default-entities/} following the naming convention
 * {@code <orgKey>-entities.yaml}, e.g. {@code processpuzzle-testbed-entities.yaml}. The pattern is
 * {@code classpath*:} so a host application can contribute its own {@code default-entities/} directory
 * alongside this library's.
 *
 * <p><strong>Existing data is never touched.</strong> A definition whose code is already present is
 * left untouched, and sample instances are not created if instances for that definition already exist.
 *
 * <p>Nothing here can fail startup: every problem is logged and the next file, definition, or instance
 * is attempted.
 */
@Component
@ConditionalOnProperty(prefix = "base-entity", name = "loadDefaultEntities", havingValue = "true")
public class DefaultEntityLoader {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultEntityLoader.class);
    private static final String ENTITIES_FILE_SUFFIX = "-entities.yaml";
    private static final String DEFAULT_ENTITIES_LOCATION = "classpath*:default-entities/*" + ENTITIES_FILE_SUFFIX;

    private final CreateEntityDefinitionUseCase createDefinitionUseCase;
    private final EntityDefinitionRepository definitionRepository;
    private final EntityDefinitionMapper definitionMapper;
    private final CreateEntityInstanceUseCase createInstanceUseCase;
    private final ResourcePatternResolver resourceResolver;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public DefaultEntityLoader(CreateEntityDefinitionUseCase createDefinitionUseCase,
                               EntityDefinitionRepository definitionRepository,
                               EntityDefinitionMapper definitionMapper,
                               CreateEntityInstanceUseCase createInstanceUseCase,
                               ResourcePatternResolver resourceResolver) {
        this.createDefinitionUseCase = createDefinitionUseCase;
        this.definitionRepository = definitionRepository;
        this.definitionMapper = definitionMapper;
        this.createInstanceUseCase = createInstanceUseCase;
        this.resourceResolver = resourceResolver;
    }

    /**
     * Ordered ahead of base-state's {@code DefaultStateImporter}, which is {@code @Order(20)}. A
     * state machine's {@code stateAttributeKey} is validated against the entity definition it names,
     * so an importer running before these definitions exist would reject every seeded machine — and
     * that importer logs rather than fails, so the application would come up with no state machines
     * and two lines in the log. Both listeners now say what they depend on instead of relying on
     * bean-registration order.
     */
    @Order(10)
    @EventListener(ApplicationReadyEvent.class)
    public void loadDefaults() {
        Resource[] resources;
        try {
            resources = resourceResolver.getResources(DEFAULT_ENTITIES_LOCATION);
        } catch (IOException e) {
            LOG.warn("Unable to scan for default entity files at {}", DEFAULT_ENTITIES_LOCATION, e);
            return;
        }

        if (resources.length == 0) {
            LOG.info("No default entity files found at {}", DEFAULT_ENTITIES_LOCATION);
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
            LOG.warn("Skipping default entity file '{}': the name does not follow the '<orgKey>{}' convention.",
                    fileName, ENTITIES_FILE_SUFFIX);
            return;
        }

        DefaultEntitiesDocument document;
        try (InputStream input = resource.getInputStream()) {
            document = yamlMapper.readValue(input, DefaultEntitiesDocument.class);
        } catch (IOException e) {
            LOG.warn("Failed to read default entity file {}", fileName, e);
            return;
        }

        Tally definitionTally = new Tally();
        for (BaseEntityDefinitionInput definition : document.entityDefinitions()) {
            definitionTally.add(createDefinition(definition, fileName));
        }

        Tally instanceTally = new Tally();
        for (EntityObjectInput entity : document.entities()) {
            instanceTally.add(createInstance(orgKey, entity, fileName));
        }

        LOG.info("Loaded default entities from {} into organization '{}': definitions (created={}, already present={}, rejected={}), instances (created={}, already present={}, rejected={})",
                fileName, orgKey,
                definitionTally.created, definitionTally.skipped, definitionTally.rejected,
                instanceTally.created, instanceTally.skipped, instanceTally.rejected);
    }

    private Outcome createDefinition(BaseEntityDefinitionInput input, String fileName) {
        if (input == null || isBlank(input.getCode())) {
            LOG.warn("Skipping an entity definition in {}: the entry is null or has no code.", fileName);
            return Outcome.REJECTED;
        }

        if (definitionRepository.existsByCode(input.getCode())) {
            LOG.info("Default entity definition '{}' already exists; left untouched.", input.getCode());
            return Outcome.SKIPPED;
        }

        try {
            BaseEntityDefinition domain = definitionMapper.toDomain(input);
            BaseEntityDefinition created = createDefinitionUseCase.create(domain);
            LOG.info("Created default entity definition '{}' as revision {}.",
                    created.getCode(), created.getVersion() == null ? 0 : created.getVersion());
            return Outcome.CREATED;
        } catch (ConflictException e) {
            LOG.info("Default entity definition '{}' already exists; left untouched.", input.getCode());
            return Outcome.SKIPPED;
        } catch (ValidationException e) {
            LOG.warn("Default entity definition '{}' from {} was rejected: {}",
                    input.getCode(), fileName, e.getMessage());
            return Outcome.REJECTED;
        } catch (RuntimeException e) {
            LOG.warn("Failed to create default entity definition '{}' from {}.",
                    input.getCode(), fileName, e);
            return Outcome.REJECTED;
        }
    }

    /**
     * @param orgKey the organization this file seeds, from its name — the same value a request path
     *               would carry, so a seeded instance's {@code EntityObjectCreatedEvent} is
     *               indistinguishable from one created over REST and its state machine starts too
     */
    private Outcome createInstance(String orgKey, EntityObjectInput input, String fileName) {
        if (input == null || isBlank(input.getEntityDefinitionCode()) || input.getPayload() == null) {
            LOG.warn("Skipping an entity instance in {}: missing entityDefinitionCode or payload.", fileName);
            return Outcome.REJECTED;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = input.getPayload();
            EntityObject created = createInstanceUseCase.create(orgKey, input.getEntityDefinitionCode(), payload);
            LOG.info("Created default entity instance id='{}' for definition '{}'.",
                    created.getId(), input.getEntityDefinitionCode());
            return Outcome.CREATED;
        } catch (ValidationException e) {
            LOG.warn("Default entity instance for definition '{}' from {} was rejected: {}",
                    input.getEntityDefinitionCode(), fileName, e.getMessage());
            return Outcome.REJECTED;
        } catch (ConflictException e) {
            LOG.warn("Default entity instance for definition '{}' from {} was rejected by conflict: {}",
                    input.getEntityDefinitionCode(), fileName, e.getMessage());
            return Outcome.REJECTED;
        } catch (RuntimeException e) {
            LOG.warn("Failed to create default entity instance for definition '{}' from {}.",
                    input.getEntityDefinitionCode(), fileName, e);
            return Outcome.REJECTED;
        }
    }

    private static String orgKeyOf(String fileName) {
        if (fileName == null || !fileName.endsWith(ENTITIES_FILE_SUFFIX)) {
            return null;
        }
        String orgKey = fileName.substring(0, fileName.length() - ENTITIES_FILE_SUFFIX.length());
        return orgKey.isBlank() ? null : orgKey;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private enum Outcome {
        CREATED, SKIPPED, REJECTED
    }

    private static final class Tally {
        private int created;
        private int skipped;
        private int rejected;

        private void add(Outcome outcome) {
            switch (outcome) {
                case CREATED -> created++;
                case SKIPPED -> skipped++;
                case REJECTED -> rejected++;
            }
        }
    }
}
