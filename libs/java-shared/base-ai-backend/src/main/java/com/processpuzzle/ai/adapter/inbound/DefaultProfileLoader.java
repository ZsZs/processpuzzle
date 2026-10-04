package com.processpuzzle.ai.adapter.inbound;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.ai.model.RecognitionProfileInput;
import com.processpuzzle.ai.usecase.RecognitionProfiles;
import com.processpuzzle.ai.usecase.exception.AiRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Seeds the bundled recognition profiles on startup, gated behind
 * {@code base-ai.loadDefaultProfiles=true} — the same arrangement as base-widget's
 * {@code DefaultWidgetLoader}: the owning organization is the part of the file name before
 * {@code -recognition-profiles.yaml}, and the directory is this feature's own, so that a host can add
 * files beside these without another library's same-named file shadowing them.
 *
 * <p><strong>Create-only.</strong> A profile already present for an entity type is left exactly as it
 * is, so a designer's tuned thresholds survive every restart. The flip side, as with base-entity's
 * seeds: changing a bundled profile does not reach a database that has already been seeded.
 *
 * <p>Nothing here can fail startup; every problem is logged and the next entry is attempted.
 */
@Component
@ConditionalOnProperty(prefix = "base-ai", name = "loadDefaultProfiles", havingValue = "true")
public class DefaultProfileLoader {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultProfileLoader.class);
    static final String FILE_SUFFIX = "-recognition-profiles.yaml";
    private static final String LOCATION = "classpath*:default-recognition-profiles/*" + FILE_SUFFIX;

    private final RecognitionProfiles profiles;
    private final AiMapper mapper;
    private final ResourcePatternResolver resources;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public DefaultProfileLoader(RecognitionProfiles profiles, AiMapper mapper, ResourcePatternResolver resources) {
        this.profiles = profiles;
        this.mapper = mapper;
        this.resources = resources;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadDefaults() {
        Resource[] files;
        try {
            files = resources.getResources(LOCATION);
        } catch (IOException e) {
            LOG.warn("Unable to scan for default recognition profiles at {}", LOCATION, e);
            return;
        }
        for (Resource file : files) {
            load(file);
        }
    }

    void load(Resource file) {
        String fileName = file.getFilename();
        if (fileName == null || !fileName.endsWith(FILE_SUFFIX) || fileName.length() == FILE_SUFFIX.length()) {
            LOG.warn("Skipping '{}': the name does not follow the '<orgKey>{}' convention.", fileName, FILE_SUFFIX);
            return;
        }
        String orgKey = fileName.substring(0, fileName.length() - FILE_SUFFIX.length());

        Document document;
        try (InputStream input = file.getInputStream()) {
            document = yaml.readValue(input, Document.class);
        } catch (IOException e) {
            LOG.warn("Failed to read default recognition profiles from {}", fileName, e);
            return;
        }
        if (document == null) {
            LOG.warn("Skipping default recognition profiles from {}: the YAML contains no document.", fileName);
            return;
        }

        int created = 0;
        int present = 0;
        int rejected = 0;
        for (RecognitionProfileInput input : document.recognitionProfiles() == null ? List.<RecognitionProfileInput>of() : document.recognitionProfiles()) {
            try {
                if (profiles.seed(orgKey, mapper.toDraft(input)).isPresent()) {
                    created++;
                } else {
                    present++;
                }
            } catch (AiRequestException | NullPointerException e) {
                LOG.warn("Default recognition profile '{}' from {} was rejected: {}",
                        input == null ? null : input.getEntityName(), fileName, e.getMessage());
                rejected++;
            }
        }
        LOG.info("Loaded default recognition profiles from {} into organization '{}': created={}, already present={}, rejected={}",
                fileName, orgKey, created, present, rejected);
    }

    /** The file's shape: a list under {@code recognitionProfiles}, each entry a RecognitionProfileInput. */
    record Document(List<RecognitionProfileInput> recognitionProfiles) {
    }
}
