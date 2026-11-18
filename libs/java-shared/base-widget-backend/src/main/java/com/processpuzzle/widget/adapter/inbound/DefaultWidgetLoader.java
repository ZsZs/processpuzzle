package com.processpuzzle.widget.adapter.inbound;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.widget.adapter.inbound.dto.DefaultWidgetsDocument;
import com.processpuzzle.widget.domain.WidgetDefinitionRepository;
import com.processpuzzle.widget.model.WidgetDefinition;
import com.processpuzzle.widget.model.WidgetDefinitionInput;
import com.processpuzzle.widget.usecase.exception.WidgetDefinitionAlreadyExistsException;
import com.processpuzzle.widget.usecase.exception.WidgetDefinitionInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Seeds the bundled default widget definitions on startup, so a fresh deployment serves a catalogue a
 * designer can place from instead of an empty palette. Gated behind
 * {@code base-widget.loadDefaultWidgets=true}.
 *
 * <p>Widget definitions are tenant-scoped, so every default has to belong to <em>some</em>
 * organization, and the owning organization is part of the file name: {@code <orgKey>-widgets.yaml}
 * lands in {@code orgKey}, so {@code processpuzzle-testbed-widgets.yaml} is loaded into
 * {@code processpuzzle-testbed}. Seeding another tenant is adding another file, exactly as
 * {@code DefaultAppLoader} treats {@code <orgKey>-apps.yaml} and {@code SampleRuleLoader}
 * {@code <orgKey>-rules.yaml}. The pattern is {@code classpath*:} rather than {@code classpath:} so a
 * host application can contribute its own {@code default-widgets/} directory alongside this library's.
 *
 * <p>Unlike base-app's loader this one does <em>not</em> provision the organization: a widget
 * definition is keyed by {@code (orgKey, key)} and this module has no organization registry to consult
 * — {@code allowedDependencies} names only {@code core} and {@code shared}. Seeding a tenant nothing
 * else knows about is therefore possible and harmless; the definitions are simply reachable under that
 * key.
 *
 * <p>Everything goes through {@link WidgetEndpoint}, not through the use case directly, so a default
 * is subject to the same mapping and the same key and port validation as a definition a designer
 * saves.
 *
 * <p><strong>The file is the source of truth for what it names.</strong> A definition that is already
 * present is brought back to the file's content when it differs, and left alone when it does not — so
 * a widget gaining a property or a description in a new release reaches a database seeded by an older
 * one. It used to be left untouched, which kept a designer's edits but also froze every persistent
 * deployment at the catalogue it was first created with: stage kept serving widgets without the props
 * added since. A widget a designer has edited therefore goes back to the file at the next start, which
 * is the point of seeding the testbed's own catalogue; a tenant whose widgets are its own does not ship
 * a file here.
 *
 * <p>Seeded definitions are created as drafts and never published here. Publishing is the designer's
 * decision, and a catalogue that arrives already published would leave the Publish action with nothing
 * to demonstrate. An update is an edit like any other, so it does not publish either.
 *
 * <p>Nothing here can fail startup: every problem is logged and the next file or definition is
 * attempted.
 */
@Component
@ConditionalOnProperty(prefix = "base-widget", name = "loadDefaultWidgets", havingValue = "true")
public class DefaultWidgetLoader {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultWidgetLoader.class);
    private static final String WIDGETS_FILE_SUFFIX = "-widgets.yaml";
    private static final String DEFAULT_WIDGETS_LOCATION = "classpath*:default-widgets/*" + WIDGETS_FILE_SUFFIX;

    private final WidgetEndpoint endpoint;
    private final WidgetDefinitionRepository repository;
    private final ResourcePatternResolver resourceResolver;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public DefaultWidgetLoader(WidgetEndpoint endpoint, WidgetDefinitionRepository repository,
                               ResourcePatternResolver resourceResolver) {
        this.endpoint = endpoint;
        this.repository = repository;
        this.resourceResolver = resourceResolver;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadDefaults() {
        Resource[] resources;
        try {
            resources = resourceResolver.getResources(DEFAULT_WIDGETS_LOCATION);
        } catch (IOException e) {
            LOG.warn("Unable to scan for default widget files at {}", DEFAULT_WIDGETS_LOCATION, e);
            return;
        }

        if (resources.length == 0) {
            LOG.info("No default widget files found at {}", DEFAULT_WIDGETS_LOCATION);
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
            LOG.warn("Skipping default widget file '{}': the name does not follow the '<orgKey>{}' convention.",
                    fileName, WIDGETS_FILE_SUFFIX);
            return;
        }

        DefaultWidgetsDocument document;
        try (InputStream input = resource.getInputStream()) {
            document = yamlMapper.readValue(input, DefaultWidgetsDocument.class);
        } catch (IOException e) {
            LOG.warn("Failed to read default widget file {}", fileName, e);
            return;
        }

        Tally tally = new Tally();
        for (WidgetDefinitionInput definition : document.widgetDefinitions()) {
            tally.add(create(orgKey, definition, fileName));
        }
        LOG.info("Loaded default widget definitions from {} into organization '{}': created={}, updated={}, unchanged={}, rejected={}",
                fileName, orgKey, tally.created, tally.updated, tally.skipped, tally.rejected);
    }

    private Outcome create(String orgKey, WidgetDefinitionInput definition, String fileName) {
        if (definition == null || isBlank(definition.getKey())) {
            LOG.warn("Skipping a widget definition in {}: the entry is null or has no key.", fileName);
            return Outcome.REJECTED;
        }

        // Checked here rather than left to the endpoint's conflict exception, which LoggingAspect would log
        // as an ERROR with a stack trace for every already-seeded widget on every restart. The catch below
        // stays for the race with a concurrent create.
        if (repository.existsByOrgKeyAndKey(orgKey, definition.getKey())) {
            return refresh(orgKey, definition, fileName);
        }

        try {
            WidgetDefinition created = endpoint.createWidgetDefinition(orgKey, definition).getBody();
            LOG.info("Created default widget definition '{}' in organization '{}' as revision {}.",
                    definition.getKey(), orgKey,
                    created == null || created.getVersion() == null ? "?" : created.getVersion());
            return Outcome.CREATED;
        } catch (WidgetDefinitionAlreadyExistsException e) {
            return alreadyPresent(definition.getKey(), orgKey);
        } catch (WidgetDefinitionInvalidException e) {
            // The whole point of a validated default is that a broken one says which part is broken.
            LOG.warn("Default widget definition '{}' from {} was rejected: {}",
                    definition.getKey(), fileName, e.getMessage());
            return Outcome.REJECTED;
        } catch (RuntimeException e) {
            LOG.warn("Failed to create default widget definition '{}' from {} in organization '{}'.",
                    definition.getKey(), fileName, orgKey, e);
            return Outcome.REJECTED;
        }
    }

    /** Brings a stored definition to the file's content, and only when it differs: an update marks the widget edited. */
    private Outcome refresh(String orgKey, WidgetDefinitionInput definition, String fileName) {
        try {
            ResponseEntity<WidgetDefinition> response = endpoint.getWidgetDefinition(orgKey, definition.getKey());
            WidgetDefinition stored = response == null ? null : response.getBody();
            if (stored == null || unchanged(stored, definition)) {
                return alreadyPresent(definition.getKey(), orgKey);
            }
            endpoint.updateWidgetDefinition(orgKey, definition.getKey(), definition);
            LOG.info("Updated default widget definition '{}' in organization '{}' to the content of {}.", definition.getKey(), orgKey, fileName);
            return Outcome.UPDATED;
        } catch (WidgetDefinitionInvalidException e) {
            LOG.warn("Default widget definition '{}' from {} was rejected: {}", definition.getKey(), fileName, e.getMessage());
            return Outcome.REJECTED;
        } catch (RuntimeException e) {
            LOG.warn("Failed to update default widget definition '{}' from {} in organization '{}'.", definition.getKey(), fileName, orgKey, e);
            return Outcome.REJECTED;
        }
    }

    /** Whether the stored widget already says what the file says; every field an update would write. */
    private static boolean unchanged(WidgetDefinition stored, WidgetDefinitionInput wanted) {
        return Objects.equals(stored.getName(), wanted.getName())
                && Objects.equals(stored.getTranslocoId(), wanted.getTranslocoId())
                && Objects.equals(stored.getDescription(), wanted.getDescription())
                && Objects.equals(stored.getCategory(), wanted.getCategory())
                && Objects.equals(stored.getIcon(), wanted.getIcon())
                && Objects.equals(stored.getPropsSchema(), wanted.getPropsSchema())
                && Objects.equals(stored.getInputPorts(), wanted.getInputPorts())
                && Objects.equals(stored.getOutputPorts(), wanted.getOutputPorts());
    }

    private static Outcome alreadyPresent(String key, String orgKey) {
        LOG.info("Default widget definition '{}' already exists in organization '{}' with the file's content; left untouched.", key, orgKey);
        return Outcome.SKIPPED;
    }

    /** The part of {@code <orgKey>-widgets.yaml} before the suffix, or {@code null} if there is none. */
    private static String orgKeyOf(String fileName) {
        if (fileName == null || !fileName.endsWith(WIDGETS_FILE_SUFFIX)) {
            return null;
        }
        String orgKey = fileName.substring(0, fileName.length() - WIDGETS_FILE_SUFFIX.length());
        return orgKey.isBlank() ? null : orgKey;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** What became of one entry, for the per-file summary. */
    private enum Outcome {
        CREATED, UPDATED, SKIPPED, REJECTED
    }

    /** The per-file summary itself. */
    private static final class Tally {
        private int created;
        private int updated;
        private int skipped;
        private int rejected;

        private void add(Outcome outcome) {
            switch (outcome) {
                case CREATED -> created++;
                case UPDATED -> updated++;
                case SKIPPED -> skipped++;
                case REJECTED -> rejected++;
            }
        }
    }
}
