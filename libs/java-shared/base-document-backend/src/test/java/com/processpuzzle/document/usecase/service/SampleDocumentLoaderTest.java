package com.processpuzzle.document.usecase.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.usecase.ImportDocuments;
import com.processpuzzle.document.usecase.ImportOutcome;
import com.processpuzzle.document.usecase.PublishDocumentTranslation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SampleDocumentLoaderTest {

    private static final String LOCATION = "classpath*:sample-documents/*-documents.yaml";

    private static final String PUBLIC_DOCUMENT = """
            documents:
              - slug: platform-overview
                title: Platform overview
                sourceLocale: en
                isPublic: true
                translations:
                  - locale: en
                  - locale: hu
            """;

    private static final String PRIVATE_DOCUMENT = """
            documents:
              - slug: release-notes
                title: Release notes
                sourceLocale: en
                isPublic: false
                translations:
                  - locale: en
            """;

    private static Level originalLevel;

    private ImportDocuments importDocuments;
    private PublishDocumentTranslation publishTranslation;
    private DocumentRepository repository;
    private ResourcePatternResolver resourceResolver;
    private SampleDocumentLoader loader;

    @BeforeAll
    static void silenceLoader() {
        // Several tests intentionally make a collaborator throw; the loader logs those at WARN,
        // which would otherwise dump stack traces over the build console.
        Logger logger = (Logger) LoggerFactory.getLogger(SampleDocumentLoader.class);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.OFF);
    }

    @AfterAll
    static void restoreLoaderLogging() {
        ((Logger) LoggerFactory.getLogger(SampleDocumentLoader.class)).setLevel(originalLevel);
    }

    @BeforeEach
    void setUp() {
        importDocuments = mock(ImportDocuments.class);
        publishTranslation = mock(PublishDocumentTranslation.class);
        repository = mock(DocumentRepository.class);
        resourceResolver = mock(ResourcePatternResolver.class);
        loader = new SampleDocumentLoader(importDocuments, publishTranslation, repository, resourceResolver);
    }

    @Test
    void loadSamples_importsEachDiscoveredFile() throws IOException {
        givenFiles(new NamedByteArrayResource("a-documents.yaml", "documents: []"),
                new NamedByteArrayResource("b-documents.yaml", "documents: []"));
        givenImportSucceeds();

        loader.loadSamples();

        verify(importDocuments).execute(eq("a"), any(InputStream.class));
        verify(importDocuments).execute(eq("b"), any(InputStream.class));
    }

    @Test
    void loadSamples_importsIntoTheOrganizationNamedByTheFile() throws IOException {
        // Documents are tenant-scoped, so the samples have to land in *some* organization; which
        // one is the part of the file name before '-documents.yaml'.
        givenFiles(new NamedByteArrayResource("processpuzzle-testbed-documents.yaml", "documents: []"));
        givenImportSucceeds();

        loader.loadSamples();

        ArgumentCaptor<String> orgKey = ArgumentCaptor.forClass(String.class);
        verify(importDocuments).execute(orgKey.capture(), any(InputStream.class));
        assertThat(orgKey.getValue()).isEqualTo("processpuzzle-testbed");
    }

    @Test
    void loadSamples_publishesEveryLocaleOfANewlyCreatedPublicDocument() throws IOException {
        // A public sample that is never published is a 404 on the public read path, which would
        // make it demonstrate the opposite of what it is for.
        givenFiles(new NamedByteArrayResource("testbed-documents.yaml", PUBLIC_DOCUMENT));
        givenImportSucceeds();
        givenDocumentImported("testbed", "platform-overview", "doc-1");

        loader.loadSamples();

        verify(publishTranslation).execute("testbed", "doc-1", "en");
        verify(publishTranslation).execute("testbed", "doc-1", "hu");
    }

    @Test
    void loadSamples_leavesANonPublicDocumentUnpublished() throws IOException {
        givenFiles(new NamedByteArrayResource("testbed-documents.yaml", PRIVATE_DOCUMENT));
        givenImportSucceeds();
        givenDocumentImported("testbed", "release-notes", "doc-2");

        loader.loadSamples();

        verify(publishTranslation, never()).execute(any(), any(), any());
    }

    @Test
    void loadSamples_doesNotRepublishADocumentThatAlreadyExisted() throws IOException {
        // Restarting against a persistent database must not undo an editor's unpublish, nor
        // overwrite the snapshot of a document they have since edited.
        givenFiles(new NamedByteArrayResource("testbed-documents.yaml", PUBLIC_DOCUMENT));
        givenImportSucceeds();
        when(repository.existsByOrgKeyAndSlug("testbed", "platform-overview")).thenReturn(true);

        loader.loadSamples();

        verify(importDocuments).execute(eq("testbed"), any(InputStream.class));
        verify(publishTranslation, never()).execute(any(), any(), any());
    }

    @Test
    void loadSamples_publishesNothingFromAFileThatImportedWithErrors() throws IOException {
        // The import is all-or-nothing, so a rejected file persisted nothing to publish.
        givenFiles(new NamedByteArrayResource("testbed-documents.yaml", PUBLIC_DOCUMENT));
        when(importDocuments.execute(any(String.class), any(InputStream.class)))
                .thenReturn(new ImportOutcome(0, 0, List.of("Entry 'platform-overview': broken")));

        loader.loadSamples();

        verify(publishTranslation, never()).execute(any(), any(), any());
    }

    @Test
    void loadSamples_continuesWhenPublishingOneLocaleFails() throws IOException {
        givenFiles(new NamedByteArrayResource("testbed-documents.yaml", PUBLIC_DOCUMENT));
        givenImportSucceeds();
        givenDocumentImported("testbed", "platform-overview", "doc-1");
        when(publishTranslation.execute("testbed", "doc-1", "en")).thenThrow(new IllegalStateException("boom"));

        loader.loadSamples();

        verify(publishTranslation).execute("testbed", "doc-1", "hu");
    }

    @Test
    void loadSamples_skipsFileWithoutAnOrgKeyPrefix() throws IOException {
        givenFiles(new NamedByteArrayResource("-documents.yaml", "documents: []"));

        loader.loadSamples();

        verify(importDocuments, never()).execute(any(String.class), any(InputStream.class));
    }

    @Test
    void loadSamples_skipsAFileThatDoesNotUseTheDocumentsSuffix() throws IOException {
        givenFiles(new NamedByteArrayResource("README.md", "not yaml"));

        loader.loadSamples();

        verify(importDocuments, never()).execute(any(String.class), any(InputStream.class));
    }

    @Test
    void loadSamples_skipsAResourceWithoutAFilename() throws IOException {
        // A Resource is not obliged to have one — ByteArrayResource itself returns null.
        givenFiles(new ByteArrayResource("documents: []".getBytes()));

        loader.loadSamples();

        verify(importDocuments, never()).execute(any(String.class), any(InputStream.class));
    }

    @Test
    void loadSamples_stillImportsAFileWhoseEntriesCannotBeParsed() throws IOException {
        // Reading the entries only informs publishing; deciding that the file is malformed is the
        // import's job, and its error message is the useful one.
        givenFiles(new NamedByteArrayResource("testbed-documents.yaml", "documents: [ this is not a document"));
        givenImportSucceeds();

        loader.loadSamples();

        verify(importDocuments).execute(eq("testbed"), any(InputStream.class));
        verify(publishTranslation, never()).execute(any(), any(), any());
    }

    @Test
    void loadSamples_givesUpWhenTheClasspathCannotBeScanned() throws IOException {
        when(resourceResolver.getResources(LOCATION)).thenThrow(new IOException("no classpath"));

        loader.loadSamples();

        verify(importDocuments, never()).execute(any(String.class), any(InputStream.class));
    }

    @Test
    void loadSamples_skipsWhenNoFilesFound() throws IOException {
        when(resourceResolver.getResources(LOCATION)).thenReturn(new Resource[0]);

        loader.loadSamples();

        verify(importDocuments, never()).execute(any(String.class), any(InputStream.class));
    }

    @Test
    void loadSamples_continuesWhenSingleFileFails() throws IOException {
        givenFiles(new NamedByteArrayResource("bad-documents.yaml", "documents: []"),
                new NamedByteArrayResource("good-documents.yaml", "documents: []"));
        when(importDocuments.execute(any(String.class), any(InputStream.class)))
                .thenThrow(new IOException("boom"))
                .thenReturn(new ImportOutcome(1, 0, List.of()));

        loader.loadSamples();

        verify(importDocuments).execute(eq("bad"), any(InputStream.class));
        verify(importDocuments).execute(eq("good"), any(InputStream.class));
    }

    @Test
    void loadSamples_skipsAFileItCannotRead() throws IOException {
        Resource unreadable = mock(Resource.class);
        when(unreadable.getFilename()).thenReturn("testbed-documents.yaml");
        when(unreadable.getInputStream()).thenThrow(new IOException("gone"));
        givenFiles(unreadable);

        loader.loadSamples();

        verify(importDocuments, never()).execute(any(String.class), any(InputStream.class));
    }

    @Test
    void loadSamples_publishesNothingFromAFileWithNoDocumentsKeyAtAll() throws IOException {
        givenFiles(new NamedByteArrayResource("testbed-documents.yaml", "documents:\n"));
        givenImportSucceeds();

        loader.loadSamples();

        verify(importDocuments).execute(eq("testbed"), any(InputStream.class));
        verify(publishTranslation, never()).execute(any(), any(), any());
    }

    @Test
    void loadSamples_ignoresAnEntryWithoutASlugWhenDecidingWhatAlreadyExisted() throws IOException {
        // The import will reject the file for it; asking the repository about a null slug beforehand
        // would fail first, and for a worse reason.
        givenFiles(new NamedByteArrayResource("testbed-documents.yaml", """
                documents:
                  - title: No slug
                    sourceLocale: en
                    isPublic: true
                """));
        givenImportSucceeds();

        loader.loadSamples();

        verify(repository, never()).existsByOrgKeyAndSlug(any(), any());
        verify(publishTranslation, never()).execute(any(), any(), any());
    }

    @Test
    void loadSamples_saysSoWhenADocumentReportedImportedCannotBeFound() throws IOException {
        givenFiles(new NamedByteArrayResource("testbed-documents.yaml", PUBLIC_DOCUMENT));
        givenImportSucceeds();
        when(repository.existsByOrgKeyAndSlug("testbed", "platform-overview")).thenReturn(false);
        when(repository.findByOrgKeyAndSlug("testbed", "platform-overview")).thenReturn(Optional.empty());

        loader.loadSamples();

        verify(publishTranslation, never()).execute(any(), any(), any());
    }

    @Test
    void loadSamples_publishesTheSourceLocaleEvenWhenTheFileNamesNoTranslationForIt() throws IOException {
        // ImportDocuments materializes the source locale as an empty draft either way, so there is
        // always something to publish.
        givenFiles(new NamedByteArrayResource("testbed-documents.yaml", """
                documents:
                  - slug: platform-overview
                    title: Platform overview
                    sourceLocale: en
                    isPublic: true
                    translations:
                """));
        givenImportSucceeds();
        givenDocumentImported("testbed", "platform-overview", "doc-1");

        loader.loadSamples();

        verify(publishTranslation).execute("testbed", "doc-1", "en");
    }

    @Test
    void loadSamples_hasNoLocaleToPublishWhenTheEntryNamesNone() throws IOException {
        givenFiles(new NamedByteArrayResource("testbed-documents.yaml", """
                documents:
                  - slug: platform-overview
                    title: Platform overview
                    isPublic: true
                    translations:
                      - blocks: []
                """));
        givenImportSucceeds();
        givenDocumentImported("testbed", "platform-overview", "doc-1");

        loader.loadSamples();

        verify(publishTranslation, never()).execute(any(), any(), any());
    }

    private void givenFiles(Resource... resources) throws IOException {
        when(resourceResolver.getResources(LOCATION)).thenReturn(resources);
    }

    private void givenImportSucceeds() throws IOException {
        when(importDocuments.execute(any(String.class), any(InputStream.class)))
                .thenReturn(new ImportOutcome(1, 0, List.of()));
    }

    private void givenDocumentImported(String orgKey, String slug, String documentId) {
        Document document = new Document(orgKey, documentId, slug, "Title", "en", null);
        when(repository.existsByOrgKeyAndSlug(orgKey, slug)).thenReturn(false);
        when(repository.findByOrgKeyAndSlug(orgKey, slug)).thenReturn(Optional.of(document));
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(String filename, String content) {
            super(content.getBytes());
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
