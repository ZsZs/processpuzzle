package com.processpuzzle.document.usecase.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.model.DocumentInput;
import com.processpuzzle.document.model.DocumentTranslationInput;
import com.processpuzzle.document.usecase.DocumentYamlFile;
import com.processpuzzle.document.usecase.ImportDocuments;
import com.processpuzzle.document.usecase.ImportOutcome;
import com.processpuzzle.document.usecase.PublishDocumentTranslation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Development convenience: imports the bundled sample documents on startup, so a fresh deployment
 * has something to read and edit instead of an empty Documents list. Gated behind
 * {@code base-document.loadSamples=true}.
 *
 * <p>Documents are tenant-scoped, so every sample has to belong to <em>some</em> organization. The
 * owning organization is part of the file name: {@code <orgKey>-documents.yaml} lands in
 * {@code orgKey}, so {@code processpuzzle-testbed-documents.yaml} is imported into
 * {@code processpuzzle-testbed}. One deployment can therefore seed several organizations, and
 * adding one is adding a file — the same convention {@code SampleRuleLoader} applies to
 * {@code <orgKey>-rules.yaml} and {@code DefaultAppLoader} to {@code <orgKey>-apps.yaml}. The
 * pattern is {@code classpath*:} rather than {@code classpath:} so a host application can
 * contribute its own {@code sample-documents/} directory alongside this library's.
 *
 * <h2>Why this publishes, when the import deliberately does not</h2>
 *
 * <p>{@link ImportDocuments} never publishes: a file someone sent must not go live on arrival.
 * That leaves a gap here, because a sample marked {@code isPublic} is not actually publicly
 * readable until a snapshot exists — the public read path serves snapshots, so an unpublished
 * public document is a 404. A sample that cannot be read anonymously would not demonstrate the
 * thing it is there to demonstrate, so this loader publishes every locale of a public sample.
 *
 * <p>It does so <strong>only for documents this run created</strong>. Whether a slug already
 * existed is decided before the import, so restarting against a persistent database cannot
 * re-publish what an editor unpublished, nor overwrite the snapshot of a document they have since
 * edited. Nothing is published from a file that imported with errors, since nothing from it was
 * persisted.
 *
 * <p>Nothing here can fail startup: every problem is logged and the next file is attempted.
 */
@Component
@ConditionalOnProperty(prefix = "base-document", name = "loadSamples", havingValue = "true")
public class SampleDocumentLoader {

    private static final Logger LOG = LoggerFactory.getLogger(SampleDocumentLoader.class);
    private static final String DOCUMENTS_FILE_SUFFIX = "-documents.yaml";
    private static final String SAMPLE_DOCUMENTS_LOCATION = "classpath*:sample-documents/*" + DOCUMENTS_FILE_SUFFIX;

    private final ImportDocuments importDocuments;
    private final PublishDocumentTranslation publishTranslation;
    private final DocumentRepository repository;
    private final ResourcePatternResolver resourceResolver;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public SampleDocumentLoader(ImportDocuments importDocuments,
                                PublishDocumentTranslation publishTranslation,
                                DocumentRepository repository,
                                ResourcePatternResolver resourceResolver) {
        this.importDocuments = importDocuments;
        this.publishTranslation = publishTranslation;
        this.repository = repository;
        this.resourceResolver = resourceResolver;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadSamples() {
        Resource[] resources;
        try {
            resources = resourceResolver.getResources(SAMPLE_DOCUMENTS_LOCATION);
        } catch (IOException e) {
            LOG.warn("Unable to scan for sample document files at {}", SAMPLE_DOCUMENTS_LOCATION, e);
            return;
        }

        if (resources.length == 0) {
            LOG.info("No sample document files found at {}", SAMPLE_DOCUMENTS_LOCATION);
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
            LOG.warn("Skipping sample document file '{}': the name does not follow the '<orgKey>{}' convention.",
                    name, DOCUMENTS_FILE_SUFFIX);
            return;
        }

        byte[] yaml;
        try (InputStream input = resource.getInputStream()) {
            yaml = input.readAllBytes();
        } catch (IOException e) {
            LOG.warn("Failed to read sample documents from {}", name, e);
            return;
        }

        // Parsed here as well as inside the import, because publishing needs to know which entries
        // are public and which locales they carry — and which of them already existed beforehand.
        List<DocumentInput> entries = parse(yaml, name);
        Set<String> alreadyPresent = presentSlugs(orgKey, entries);

        ImportOutcome outcome;
        try {
            outcome = importDocuments.execute(orgKey, new ByteArrayInputStream(yaml));
        } catch (IOException | RuntimeException e) {
            LOG.warn("Failed to import sample documents from {}", name, e);
            return;
        }

        LOG.info("Imported sample documents from {} into organization '{}': created={}, updated={}, errors={}",
                name, orgKey, outcome.created(), outcome.updated(), outcome.errors().size());
        outcome.errors().forEach(error -> LOG.warn("Sample document import error in {}: {}", name, error));
        if (!outcome.errors().isEmpty()) {
            // The import is all-or-nothing, so nothing was persisted and there is nothing to publish.
            return;
        }

        publishNewPublicDocuments(orgKey, entries, alreadyPresent, name);
    }

    /** The file's entries, or an empty list when it cannot be parsed — the import will report why. */
    private List<DocumentInput> parse(byte[] yaml, String name) {
        try {
            DocumentYamlFile file = yamlMapper.readValue(new ByteArrayInputStream(yaml), DocumentYamlFile.class);
            return file.documents() == null ? List.of() : file.documents();
        } catch (IOException e) {
            LOG.warn("Could not read the entries of {}; its documents will be imported but not published.", name, e);
            return List.of();
        }
    }

    /** Which of the file's slugs the organization already holds, decided before anything is written. */
    private Set<String> presentSlugs(String orgKey, List<DocumentInput> entries) {
        Set<String> present = new LinkedHashSet<>();
        for (DocumentInput entry : entries) {
            if (entry.getSlug() != null && repository.existsByOrgKeyAndSlug(orgKey, entry.getSlug())) {
                present.add(entry.getSlug());
            }
        }
        return present;
    }

    private void publishNewPublicDocuments(String orgKey, List<DocumentInput> entries,
                                           Set<String> alreadyPresent, String name) {
        for (DocumentInput entry : entries) {
            if (!Boolean.TRUE.equals(entry.getIsPublic()) || alreadyPresent.contains(entry.getSlug())) {
                continue;
            }
            Optional<Document> imported = repository.findByOrgKeyAndSlug(orgKey, entry.getSlug());
            if (imported.isEmpty()) {
                LOG.warn("Sample document '{}' from {} was reported imported but cannot be found.",
                        entry.getSlug(), name);
                continue;
            }
            for (String locale : localesOf(entry)) {
                publish(orgKey, imported.get().getId(), entry.getSlug(), locale);
            }
        }
    }

    private void publish(String orgKey, String documentId, String slug, String locale) {
        try {
            publishTranslation.execute(orgKey, documentId, locale);
            LOG.info("Published locale '{}' of the public sample document '{}' in organization '{}'.",
                    locale, slug, orgKey);
        } catch (RuntimeException e) {
            // A sample that will not publish is worth naming, but is not worth failing startup over:
            // the document itself is imported and an editor can publish it by hand.
            LOG.warn("Could not publish locale '{}' of sample document '{}' in organization '{}'.",
                    locale, slug, orgKey, e);
        }
    }

    /**
     * Every locale the import created a draft for: the declared translations, plus {@code
     * sourceLocale}, which {@link ImportDocuments} materializes as an empty draft even when the
     * file names no translation for it.
     */
    private static List<String> localesOf(DocumentInput entry) {
        Set<String> locales = new LinkedHashSet<>();
        if (entry.getTranslations() != null) {
            for (DocumentTranslationInput translation : entry.getTranslations()) {
                if (translation.getLocale() != null) {
                    locales.add(translation.getLocale());
                }
            }
        }
        if (entry.getSourceLocale() != null) {
            locales.add(entry.getSourceLocale());
        }
        return new ArrayList<>(locales);
    }

    /** The part of {@code <orgKey>-documents.yaml} before the suffix, or {@code null} if there is none. */
    private static String orgKeyOf(String filename) {
        if (filename == null || !filename.endsWith(DOCUMENTS_FILE_SUFFIX)) {
            return null;
        }
        String orgKey = filename.substring(0, filename.length() - DOCUMENTS_FILE_SUFFIX.length());
        return orgKey.isBlank() ? null : orgKey;
    }
}
