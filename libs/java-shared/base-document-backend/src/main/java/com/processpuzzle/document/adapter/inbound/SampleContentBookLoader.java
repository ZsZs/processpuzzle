package com.processpuzzle.document.adapter.inbound;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.document.domain.ContentBookNode;
import com.processpuzzle.document.domain.ContentBookRepository;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentRoles;
import com.processpuzzle.document.usecase.ContentBookCommand;
import com.processpuzzle.document.usecase.CreateContentBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Development convenience: creates the bundled sample content books on startup, after
 * {@link SampleDocumentLoader} has imported the documents they show. Gated behind the same
 * {@code base-document.loadSamples=true}.
 *
 * <p>The owning organization comes from the file name, {@code <orgKey>-content-books.yaml}, exactly as
 * for sample documents. Nodes name their document by {@code documentSlug}: document ids are generated
 * on import, so a slug is the only reference a seed file can know in advance.
 *
 * <p><b>Create-only.</b> A book whose slug already exists is left alone, so a restart against a
 * persistent database never reverts a tree an editor has since rearranged. A book naming a slug the
 * organization does not hold is skipped as a whole rather than created with a hole in it.
 *
 * <p>Nothing here can fail startup: every problem is logged and the next book is attempted.
 */
@Component
@ConditionalOnProperty(prefix = "base-document", name = "loadSamples", havingValue = "true")
public class SampleContentBookLoader {

    private static final Logger LOG = LoggerFactory.getLogger(SampleContentBookLoader.class);
    private static final String FILE_SUFFIX = "-content-books.yaml";
    private static final String LOCATION = "classpath*:sample-content-books/*" + FILE_SUFFIX;

    private final CreateContentBook createContentBook;
    private final ContentBookRepository books;
    private final DocumentRepository documents;
    private final ResourcePatternResolver resourceResolver;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public SampleContentBookLoader(CreateContentBook createContentBook, ContentBookRepository books,
                                   DocumentRepository documents, ResourcePatternResolver resourceResolver) {
        this.createContentBook = createContentBook;
        this.books = books;
        this.documents = documents;
        this.resourceResolver = resourceResolver;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(SampleDocumentLoader.ORDER + 1)
    public void loadSamples() {
        Resource[] resources;
        try {
            resources = resourceResolver.getResources(LOCATION);
        } catch (IOException e) {
            LOG.warn("Unable to scan for sample content book files at {}", LOCATION, e);
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
            LOG.warn("Skipping sample content book file '{}': the name does not follow the '<orgKey>{}' convention.",
                    name, FILE_SUFFIX);
            return;
        }

        SampleFile file;
        try (InputStream input = resource.getInputStream()) {
            file = yaml.readValue(input, SampleFile.class);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Failed to read sample content books from {}", name, e);
            return;
        }
        if (file.contentBooks() == null) {
            return;
        }
        for (SampleBook book : file.contentBooks()) {
            createIfAbsent(orgKey, book, name);
        }
    }

    private void createIfAbsent(String orgKey, SampleBook book, String name) {
        if (book.slug() == null || books.existsByOrgKeyAndSlug(orgKey, book.slug())) {
            return;
        }
        Optional<List<ContentBookNode>> nodes = resolve(orgKey, book, name);
        if (nodes.isEmpty()) {
            return;
        }
        try {
            createContentBook.execute(orgKey, new ContentBookCommand(book.slug(), book.title(), book.description(),
                    Boolean.TRUE.equals(book.isPublic()),
                    new DocumentRoles(book.readerRoles(), book.editorRoles(), List.of()), nodes.get()));
            LOG.info("Created sample content book '{}' in organization '{}' from {}.", book.slug(), orgKey, name);
        } catch (RuntimeException e) {
            LOG.warn("Could not create sample content book '{}' from {}.", book.slug(), name, e);
        }
    }

    /** The nodes with every {@code documentSlug} replaced by the id, or empty when a slug is unknown. */
    private Optional<List<ContentBookNode>> resolve(String orgKey, SampleBook book, String name) {
        List<ContentBookNode> resolved = new ArrayList<>();
        for (SampleNode node : book.nodes() == null ? List.<SampleNode>of() : book.nodes()) {
            Optional<Document> document = documents.findByOrgKeyAndSlug(orgKey, node.documentSlug());
            if (document.isEmpty()) {
                LOG.warn("Skipping sample content book '{}' from {}: organization '{}' has no document '{}'.",
                        book.slug(), name, orgKey, node.documentSlug());
                return Optional.empty();
            }
            resolved.add(new ContentBookNode(node.id(), document.get().getId(), node.parentId(),
                    node.orderIndex() == null ? resolved.size() : node.orderIndex()));
        }
        return Optional.of(resolved);
    }

    private static String orgKeyOf(String filename) {
        if (filename == null || !filename.endsWith(FILE_SUFFIX)) {
            return null;
        }
        String orgKey = filename.substring(0, filename.length() - FILE_SUFFIX.length());
        return orgKey.isBlank() ? null : orgKey;
    }

    record SampleFile(List<SampleBook> contentBooks) {
    }

    record SampleBook(String slug, String title, String description, Boolean isPublic, List<String> readerRoles,
                      List<String> editorRoles, List<SampleNode> nodes) {
    }

    record SampleNode(String id, String documentSlug, String parentId, Integer orderIndex) {
    }
}
