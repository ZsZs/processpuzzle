package com.processpuzzle.document.usecase;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.PublishedDocument;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.usecase.exception.DocumentFileStoreUnavailableException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentPublishingConflictException;
import com.processpuzzle.document.usecase.port.DocumentFileStore;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Archives a document: writes a JSON snapshot of it to the file store and locks it from further edits.
 *
 * <p>The snapshot is the whole document — its properties and, per locale, both the draft and the published
 * content with their revisions — so that what was archived can be read back without this module. Published
 * content stays served after archiving; the lock is {@link DocumentGuard#requireWritable}.
 *
 * <p>Requires a publisher, as withdrawing content does. The file is written before the document is marked,
 * so a failed upload leaves it editable and the attempt can be repeated.
 */
@Service
@Transactional
public class ArchiveDocument {

    public static final String JSON = "application/json";

    private static final ObjectMapper MAPPER = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final DocumentRepository repository;
    private final DocumentDraftRepository draftRepository;
    private final PublishedDocumentRepository publishedRepository;
    private final DocumentTranslationAssembler assembler;
    private final DocumentGuard guard;
    private final ObjectProvider<DocumentFileStore> fileStore;

    public ArchiveDocument(DocumentRepository repository, DocumentDraftRepository draftRepository,
                           PublishedDocumentRepository publishedRepository, DocumentTranslationAssembler assembler,
                           DocumentGuard guard, ObjectProvider<DocumentFileStore> fileStore) {
        this.repository = repository;
        this.draftRepository = draftRepository;
        this.publishedRepository = publishedRepository;
        this.assembler = assembler;
        this.guard = guard;
        this.fileStore = fileStore;
    }

    public DocumentDetails execute(String orgKey, String documentId) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));
        guard.requirePublisher(document);
        if (document.isArchived()) {
            throw DocumentPublishingConflictException.archived(documentId);
        }
        DocumentFileStore store = fileStore.getIfUnique();
        if (store == null) {
            throw new DocumentFileStoreUnavailableException("No file store is configured for archived documents.");
        }

        Instant now = Instant.now();
        String principal = guard.currentPrincipal();
        String name = document.getSlug() + "-archive.json";
        DocumentFileStore.StoredFile stored = store.store(snapshot(document, now, principal), name, JSON);
        document.archive(now, principal, stored.bucket(), stored.objectId(), name);
        Document saved = repository.save(document);
        return new DocumentDetails(saved, null, assembler.statesOf(saved));
    }

    private byte[] snapshot(Document document, Instant archivedAt, String archivedBy) {
        Map<String, PublishedDocument> published = publishedRepository
                .findByOrgKeyAndDocumentId(document.getOrgKey(), document.getId()).stream()
                .collect(Collectors.toMap(PublishedDocument::getLocale, Function.identity()));
        List<Map<String, Object>> translations = draftRepository
                .findByOrgKeyAndDocumentId(document.getOrgKey(), document.getId()).stream()
                .map(draft -> translation(draft, published.get(draft.getLocale())))
                .toList();

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", document.getId());
        snapshot.put("orgKey", document.getOrgKey());
        snapshot.put("slug", document.getSlug());
        snapshot.put("kind", document.getKind());
        snapshot.put("title", document.getTitle());
        snapshot.put("subject", document.getSubject());
        snapshot.put("description", document.getDescription());
        snapshot.put("author", document.getAuthor());
        snapshot.put("sourceLocale", document.getSourceLocale());
        snapshot.put("isPublic", document.isPublic());
        snapshot.put("roles", document.getRoles());
        snapshot.put("ports", document.getPorts());
        snapshot.put("createdBy", document.getCreatedBy());
        snapshot.put("createdAt", Objects.toString(document.getCreatedAt(), null));
        snapshot.put("publishedAt", Objects.toString(document.getPublishedAt(), null));
        snapshot.put("archivedAt", archivedAt.toString());
        snapshot.put("archivedBy", archivedBy);
        snapshot.put("translations", translations);
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Document '" + document.getId() + "' could not be written as JSON", e);
        }
    }

    private static Map<String, Object> translation(DocumentDraft draft, PublishedDocument published) {
        Map<String, Object> translation = new LinkedHashMap<>();
        translation.put("locale", draft.getLocale());
        translation.put("revision", draft.getRevision());
        translation.put("draft", draft.getBlocks());
        if (published != null) {
            translation.put("publishedRevision", published.getPublishedRevision());
            translation.put("publishedAt", Objects.toString(published.getPublishedAt(), null));
            translation.put("publishedBy", published.getPublishedBy());
            translation.put("published", published.getBlocks());
        }
        return translation;
    }
}
