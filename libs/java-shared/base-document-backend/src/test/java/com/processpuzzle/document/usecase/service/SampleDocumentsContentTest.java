package com.processpuzzle.document.usecase.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.model.DocumentInput;
import com.processpuzzle.document.model.DocumentTranslationInput;
import com.processpuzzle.document.usecase.DocumentValidationProblem;
import com.processpuzzle.document.usecase.DocumentYamlFile;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the bundled sample file itself, not the loader around it. Two things can silently break
 * it and neither shows up until a deployment starts: an unknown property (the import mapper fails
 * on those) and a dangling reference (blocking problems abort the whole import). Both are cheap to
 * assert here and expensive to discover in a startup log.
 */
class SampleDocumentsContentTest {

    private static final String SAMPLE_FILE = "sample-documents/processpuzzle-testbed-documents.yaml";

    /** Configured exactly as {@code ImportDocuments}, so an unknown property fails here too. */
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private final DocumentMapper mapper = new DocumentMapper();
    private final DocumentReferentialIntegrityChecker checker = new DocumentReferentialIntegrityChecker();

    @Test
    void theTestbedSampleFileIsImportable() throws IOException {
        List<DocumentInput> documents = readSamples();

        assertThat(documents).hasSize(2);
        for (DocumentInput document : documents) {
            assertThat(document.getSlug()).isNotBlank();
            assertThat(document.getSourceLocale()).isNotBlank();
            for (DocumentTranslationInput translation : document.getTranslations()) {
                DocumentContent content = mapper.toContentOrNull(translation);
                List<DocumentValidationProblem> blocking = DocumentValidationProblem.blocking(
                        checker.check(mapper.toPorts(document), content));
                assertThat(blocking)
                        .as("%s / %s", document.getSlug(), translation.getLocale())
                        .isEmpty();
            }
        }
    }

    @Test
    void oneSampleIsPublicAndComplete() throws IOException {
        DocumentInput overview = sample("platform-overview");

        // Public, so the loader publishes it and an anonymous reader gets content.
        assertThat(overview.getIsPublic()).isTrue();
        assertThat(overview.getTranslations()).extracting(DocumentTranslationInput::getLocale)
                .containsExactly("en", "hu");
        assertThat(overview.getInputPorts()).isNotEmpty();
        assertThat(overview.getOutputPorts()).isNotEmpty();
        // Every widget binding resolves to a declared port — the check above would have caught a
        // typo, but only if there is a binding to check in the first place.
        assertThat(overview.getTranslations().getFirst().getBlocks())
                .anyMatch(block -> block.getInputBindings() != null && !block.getInputBindings().isEmpty());
    }

    @Test
    void theOtherSampleIsASketchInEditorialState() throws IOException {
        DocumentInput sketch = sample("testbed-release-notes");

        assertThat(sketch.getIsPublic()).isFalse();
        assertThat(sketch.getTranslations()).hasSize(1);
        // Prose only: a sketch has nothing wired up yet, so nothing here can be published by
        // accident either — the loader only publishes public documents.
        assertThat(sketch.getInputPorts()).isEmpty();
        assertThat(sketch.getOutputPorts()).isEmpty();
        assertThat(sketch.getTranslations().getFirst().getBlocks())
                .allMatch(block -> block.getType() == null);
    }

    private DocumentInput sample(String slug) throws IOException {
        return readSamples().stream()
                .filter(document -> slug.equals(document.getSlug()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No sample document with slug '" + slug + "'."));
    }

    private List<DocumentInput> readSamples() throws IOException {
        try (InputStream input = new ClassPathResource(SAMPLE_FILE).getInputStream()) {
            return yamlMapper.readValue(input, DocumentYamlFile.class).documents();
        }
    }
}
