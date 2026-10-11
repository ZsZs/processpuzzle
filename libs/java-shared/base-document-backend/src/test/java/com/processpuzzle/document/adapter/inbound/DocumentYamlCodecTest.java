package com.processpuzzle.document.adapter.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.model.DocumentInput;
import com.processpuzzle.document.usecase.DocumentCommand;
import com.processpuzzle.document.usecase.ExportDocument;
import com.processpuzzle.document.usecase.ImportDocuments;
import com.processpuzzle.document.usecase.TestGuards;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentYamlCodecTest {
    private final DocumentMapper mapper = new DocumentMapper();
    private final DocumentYamlCodec codec = new DocumentYamlCodec(mapper);

    @Test
    void httpJsonAndYamlPreserveOmittedNullAndEmptyTranslationContent() throws Exception {
        String json = """
                {"slug":"s","title":"Title","sourceLocale":"en","translations":[
                  {"locale":"en"},{"locale":"de","blocks":null},{"locale":"fr","blocks":[]}]}
                """;
        var fromHttp = mapper.toCommand(new ObjectMapper().readValue(json, DocumentInput.class));
        var fromYaml = codec.read(bytes("""
                documents:
                  - slug: s
                    title: Title
                    sourceLocale: en
                    translations:
                      - locale: en
                      - locale: de
                        blocks: null
                      - locale: fr
                        blocks: []
                """)).getFirst();

        assertThat(fromYaml).isEqualTo(fromHttp);
        assertThat(fromYaml.translations().get(0).content()).isNull();
        assertThat(fromYaml.translations().get(1).content()).isNull();
        assertThat(fromYaml.translations().get(2).content().blocks()).isEmpty();
    }

    @Test
    void omittedNullAndEmptyTranslationCollectionsAllMeanNoReplacements() throws Exception {
        for (String field : List.of("", ",\"translations\":null", ",\"translations\":[]")) {
            var input = new ObjectMapper().readValue(
                    "{\"slug\":\"s\",\"title\":\"Title\",\"sourceLocale\":\"en\"" + field + "}", DocumentInput.class);
            assertThat(mapper.toCommand(input).translations()).isEmpty();
        }
    }

    @Test
    void exportedSamplesRoundTripThroughTheRealImporterWithPortsAndBlockReferencesIntact() throws Exception {
        List<DocumentCommand> samples;
        try (var input = new ClassPathResource("sample-documents/processpuzzle-testbed-documents.yaml").getInputStream()) {
            samples = codec.read(input);
        }
        assertThat(samples).isNotEmpty();
        for (DocumentCommand sample : samples) {
            String id = UUID.randomUUID().toString();
            Document source = sample.properties().create("source", id, "ada");
            List<DocumentDraft> sourceDrafts = sample.translations().stream()
                    .map(translation -> new DocumentDraft("source", id, translation.locale(),
                            translation.contentOrEmpty(), null)).toList();
            var sourceDocuments = mock(DocumentRepository.class);
            var sourceDraftRepository = mock(DocumentDraftRepository.class);
            when(sourceDocuments.findByOrgKeyAndId("source", id)).thenReturn(Optional.of(source));
            when(sourceDraftRepository.findByOrgKeyAndDocumentId("source", id)).thenReturn(sourceDrafts);
            String exported = codec.write(new ExportDocument(sourceDocuments, sourceDraftRepository, TestGuards.permitAll())
                    .execute("source", id));
            assertThat(exported).doesNotContain("publishedAt:", "publishedRevision:", "lockVersion:", "status:");

            var targetDocuments = mock(DocumentRepository.class);
            var targetDrafts = mock(DocumentDraftRepository.class);
            var targetPublications = mock(PublishedDocumentRepository.class);
            var outcome = new ImportDocuments(targetDocuments, targetDrafts, targetPublications,
                    new DocumentReferentialIntegrityChecker(), TestGuards.permitAll())
                    .execute("target", codec.read(bytes(exported)));

            assertThat(outcome.errors()).isEmpty();
            assertThat(outcome.created()).isEqualTo(1);
            var saved = org.mockito.ArgumentCaptor.forClass(Document.class);
            verify(targetDocuments).save(saved.capture());
            assertThat(saved.getValue().getId()).isNotEqualTo(id);
            assertThat(saved.getValue().getOrgKey()).isEqualTo("target");
            assertThat(saved.getValue().getPorts()).isEqualTo(source.getPorts());
            assertThat(saved.getValue().getRoles()).isEqualTo(source.getRoles());
            assertThat(saved.getValue().getSlug()).isEqualTo(source.getSlug());
            var content = org.mockito.ArgumentCaptor.forClass(DocumentDraft.class);
            verify(targetDrafts, org.mockito.Mockito.times(sourceDrafts.size())).save(content.capture());
            for (DocumentDraft draft : sourceDrafts) {
                assertThat(content.getAllValues()).filteredOn(savedDraft -> savedDraft.getLocale().equals(draft.getLocale()))
                        .singleElement().satisfies(savedDraft -> assertThat(savedDraft.getContent()).isEqualTo(draft.getContent()));
            }
            verify(targetPublications, never()).save(any());
        }
    }

    private static ByteArrayInputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
