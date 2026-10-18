package com.processpuzzle.document.adapter.inbound;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.processpuzzle.document.domain.ContentBookNode;
import com.processpuzzle.document.domain.ContentBookRepository;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.usecase.ContentBookCommand;
import com.processpuzzle.document.usecase.CreateContentBook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SampleContentBookLoaderTest {

    private static final String LOCATION = "classpath*:sample-content-books/*-content-books.yaml";

    private static final String HANDBOOK = """
            contentBooks:
              - slug: handbook
                title: Handbook
                isPublic: true
                editorRoles: [editor]
                nodes:
                  - id: root
                    documentSlug: overview
                  - id: child
                    documentSlug: details
                    parentId: root
            """;

    private static Level originalLevel;

    private CreateContentBook createContentBook;
    private ContentBookRepository books;
    private DocumentRepository documents;
    private ResourcePatternResolver resourceResolver;
    private SampleContentBookLoader loader;

    @BeforeAll
    static void silenceLoader() {
        Logger logger = (Logger) LoggerFactory.getLogger(SampleContentBookLoader.class);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.OFF);
    }

    @AfterAll
    static void restoreLoaderLogging() {
        ((Logger) LoggerFactory.getLogger(SampleContentBookLoader.class)).setLevel(originalLevel);
    }

    @BeforeEach
    void setUp() {
        createContentBook = mock(CreateContentBook.class);
        books = mock(ContentBookRepository.class);
        documents = mock(DocumentRepository.class);
        resourceResolver = mock(ResourcePatternResolver.class);
        loader = new SampleContentBookLoader(createContentBook, books, documents, resourceResolver);
        givenDocument("overview", "11111111-1111-1111-1111-111111111111");
        givenDocument("details", "22222222-2222-2222-2222-222222222222");
    }

    @Test
    void createsTheBookInTheOrganizationNamedByTheFileWithSlugsResolvedToIds() throws IOException {
        givenFiles(new NamedResource("testbed-content-books.yaml", HANDBOOK));

        loader.loadSamples();

        ArgumentCaptor<ContentBookCommand> command = ArgumentCaptor.forClass(ContentBookCommand.class);
        verify(createContentBook).execute(eq("testbed"), command.capture());
        assertThat(command.getValue().slug()).isEqualTo("handbook");
        assertThat(command.getValue().isPublic()).isTrue();
        assertThat(command.getValue().roles().editorRoles()).containsExactly("editor");
        assertThat(command.getValue().nodes()).containsExactly(
                new ContentBookNode("root", "11111111-1111-1111-1111-111111111111", null, 0),
                new ContentBookNode("child", "22222222-2222-2222-2222-222222222222", "root", 1));
    }

    @Test
    void anExistingBookIsLeftAlone() throws IOException {
        // Create-only: a restart must not revert a tree an editor has rearranged since.
        givenFiles(new NamedResource("testbed-content-books.yaml", HANDBOOK));
        when(books.existsByOrgKeyAndSlug("testbed", "handbook")).thenReturn(true);

        loader.loadSamples();

        verify(createContentBook, never()).execute(any(), any());
    }

    @Test
    void aBookNamingAnUnknownDocumentIsSkipped() throws IOException {
        givenFiles(new NamedResource("testbed-content-books.yaml", HANDBOOK));
        when(documents.findByOrgKeyAndSlug("testbed", "details")).thenReturn(Optional.empty());

        loader.loadSamples();

        verify(createContentBook, never()).execute(any(), any());
    }

    @Test
    void aFailingCreateDoesNotStopStartup() throws IOException {
        givenFiles(new NamedResource("testbed-content-books.yaml", HANDBOOK));
        when(createContentBook.execute(anyString(), any())).thenThrow(new IllegalStateException("boom"));

        loader.loadSamples();

        verify(createContentBook).execute(eq("testbed"), any());
    }

    @Test
    void filesNotFollowingTheNamingConventionOrUnreadableAreSkipped() throws IOException {
        givenFiles(new NamedResource("content-books.yaml", HANDBOOK),
                new NamedResource("broken-content-books.yaml", "contentBooks: [ :"),
                new NamedResource("empty-content-books.yaml", "{}"));

        loader.loadSamples();

        verify(createContentBook, never()).execute(any(), any());
    }

    @Test
    void anUnscannableClasspathIsLoggedNotThrown() throws IOException {
        when(resourceResolver.getResources(LOCATION)).thenThrow(new IOException("no classpath"));

        loader.loadSamples();

        verify(createContentBook, never()).execute(any(), any());
    }

    @Test
    void theBundledHandbookNamesOnlyBundledDocuments() throws IOException {
        // The seed resolves by slug at startup; a typo here would silently drop the whole book.
        String seed = new ClassPathResource("sample-content-books/processpuzzle-testbed-content-books.yaml")
                .getContentAsString(StandardCharsets.UTF_8);
        String documentsSeed = new ClassPathResource("sample-documents/processpuzzle-testbed-documents.yaml")
                .getContentAsString(StandardCharsets.UTF_8);

        seed.lines().filter(line -> line.contains("documentSlug:"))
                .map(line -> line.substring(line.indexOf(':') + 1).trim())
                .forEach(slug -> assertThat(documentsSeed).contains("- slug: " + slug));
    }

    private void givenDocument(String slug, String id) {
        when(documents.findByOrgKeyAndSlug("testbed", slug))
                .thenReturn(Optional.of(new Document("testbed", id, slug, slug, "en", "ada")));
    }

    private void givenFiles(Resource... resources) throws IOException {
        when(resourceResolver.getResources(LOCATION)).thenReturn(resources);
    }

    private static final class NamedResource extends ByteArrayResource {
        private final String filename;

        NamedResource(String filename, String content) {
            super(content.getBytes(StandardCharsets.UTF_8));
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
