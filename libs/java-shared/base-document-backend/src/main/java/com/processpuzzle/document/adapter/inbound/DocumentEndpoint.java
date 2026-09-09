package com.processpuzzle.document.adapter.inbound;

import com.processpuzzle.core.logging.LogClass;
import com.processpuzzle.document.api.BaseDocumentApi;
import com.processpuzzle.document.model.Document;
import com.processpuzzle.document.model.DocumentBlock;
import com.processpuzzle.document.model.DocumentBlockInput;
import com.processpuzzle.document.model.DocumentInput;
import com.processpuzzle.document.model.DocumentPropertiesInput;
import com.processpuzzle.document.model.DocumentTranslation;
import com.processpuzzle.document.model.DocumentTranslationInput;
import com.processpuzzle.document.model.DocumentTranslationSummary;
import com.processpuzzle.document.model.PageOfDocumentSummary;
import com.processpuzzle.document.model.PublishedContent;
import com.processpuzzle.document.model.ReorderBlocksRequest;
import com.processpuzzle.document.model.ValidationResult;
import com.processpuzzle.document.usecase.AddDocumentTranslation;
import com.processpuzzle.document.usecase.AppendDocumentBlock;
import com.processpuzzle.document.usecase.CreateDocument;
import com.processpuzzle.document.usecase.DeleteDocument;
import com.processpuzzle.document.usecase.DeleteDocumentBlock;
import com.processpuzzle.document.usecase.DiscardDocumentDraft;
import com.processpuzzle.document.usecase.DocumentDetails;
import com.processpuzzle.document.usecase.ExportDocument;
import com.processpuzzle.document.usecase.FindAllDocuments;
import com.processpuzzle.document.usecase.FindDocument;
import com.processpuzzle.document.usecase.FindDocumentTranslations;
import com.processpuzzle.document.usecase.FindPublishedContent;
import com.processpuzzle.document.usecase.ImportDocuments;
import com.processpuzzle.document.usecase.PublishDocumentTranslation;
import com.processpuzzle.document.usecase.RemoveDocumentTranslation;
import com.processpuzzle.document.usecase.ReorderDocumentBlocks;
import com.processpuzzle.document.usecase.ReplaceDocumentBlock;
import com.processpuzzle.document.usecase.UnpublishDocumentTranslation;
import com.processpuzzle.document.usecase.UpdateDocument;
import com.processpuzzle.document.usecase.UpdateDocumentProperties;
import com.processpuzzle.document.usecase.ValidateDocument;
import com.processpuzzle.shared.model.ImportResult;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * A thin delegator over the use cases; paths and verbs live in base-document-api.yaml, which
 * generates {@link BaseDocumentApi}.
 *
 * <p>The generated {@code documentId} is a {@link UUID} while the use cases take the canonical
 * string — see {@code DocumentMapper} for why the domain stores a string — so every method that
 * takes an id calls {@code toString()} on the way in. That is the only translation this class does.
 */
@RestController
@LogClass
public class DocumentEndpoint implements BaseDocumentApi {

    private final CreateDocument createDocument;
    private final UpdateDocument updateDocument;
    private final UpdateDocumentProperties updateDocumentProperties;
    private final DeleteDocument deleteDocument;
    private final FindDocument findDocument;
    private final FindAllDocuments findAllDocuments;
    private final FindDocumentTranslations findDocumentTranslations;
    private final AddDocumentTranslation addDocumentTranslation;
    private final RemoveDocumentTranslation removeDocumentTranslation;
    private final PublishDocumentTranslation publishDocumentTranslation;
    private final UnpublishDocumentTranslation unpublishDocumentTranslation;
    private final DiscardDocumentDraft discardDocumentDraft;
    private final FindPublishedContent findPublishedContent;
    private final AppendDocumentBlock appendDocumentBlock;
    private final ReplaceDocumentBlock replaceDocumentBlock;
    private final DeleteDocumentBlock deleteDocumentBlock;
    private final ReorderDocumentBlocks reorderDocumentBlocks;
    private final ValidateDocument validateDocument;
    private final ImportDocuments importDocuments;
    private final ExportDocument exportDocument;
    private final DocumentMapper mapper;

    @SuppressWarnings("java:S107") // one constructor parameter per use case; the alternative is a service locator
    public DocumentEndpoint(CreateDocument createDocument,
                            UpdateDocument updateDocument,
                            UpdateDocumentProperties updateDocumentProperties,
                            DeleteDocument deleteDocument,
                            FindDocument findDocument,
                            FindAllDocuments findAllDocuments,
                            FindDocumentTranslations findDocumentTranslations,
                            AddDocumentTranslation addDocumentTranslation,
                            RemoveDocumentTranslation removeDocumentTranslation,
                            PublishDocumentTranslation publishDocumentTranslation,
                            UnpublishDocumentTranslation unpublishDocumentTranslation,
                            DiscardDocumentDraft discardDocumentDraft,
                            FindPublishedContent findPublishedContent,
                            AppendDocumentBlock appendDocumentBlock,
                            ReplaceDocumentBlock replaceDocumentBlock,
                            DeleteDocumentBlock deleteDocumentBlock,
                            ReorderDocumentBlocks reorderDocumentBlocks,
                            ValidateDocument validateDocument,
                            ImportDocuments importDocuments,
                            ExportDocument exportDocument,
                            DocumentMapper mapper) {
        this.createDocument = createDocument;
        this.updateDocument = updateDocument;
        this.updateDocumentProperties = updateDocumentProperties;
        this.deleteDocument = deleteDocument;
        this.findDocument = findDocument;
        this.findAllDocuments = findAllDocuments;
        this.findDocumentTranslations = findDocumentTranslations;
        this.addDocumentTranslation = addDocumentTranslation;
        this.removeDocumentTranslation = removeDocumentTranslation;
        this.publishDocumentTranslation = publishDocumentTranslation;
        this.unpublishDocumentTranslation = unpublishDocumentTranslation;
        this.discardDocumentDraft = discardDocumentDraft;
        this.findPublishedContent = findPublishedContent;
        this.appendDocumentBlock = appendDocumentBlock;
        this.replaceDocumentBlock = replaceDocumentBlock;
        this.deleteDocumentBlock = deleteDocumentBlock;
        this.reorderDocumentBlocks = reorderDocumentBlocks;
        this.validateDocument = validateDocument;
        this.importDocuments = importDocuments;
        this.exportDocument = exportDocument;
        this.mapper = mapper;
    }

    // ── Documents ───────────────────────────────────────────────

    @Override
    public ResponseEntity<PageOfDocumentSummary> listDocuments(String orgKey, String where, String order,
                                                               Integer page, Integer size) {
        FindAllDocuments.Result result = findAllDocuments.execute(orgKey, where, order, page, size);
        return ResponseEntity.ok(mapper.toModel(result.page(), result.statesByDocumentId()));
    }

    @Override
    public ResponseEntity<Document> createDocument(String orgKey, DocumentInput documentInput) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toModel(createDocument.execute(orgKey, documentInput)));
    }

    @Override
    public ResponseEntity<Document> getDocument(String orgKey, UUID documentId, String locale, Boolean draft) {
        return ResponseEntity.ok(toModel(
                findDocument.execute(orgKey, documentId.toString(), locale, Boolean.TRUE.equals(draft))));
    }

    @Override
    public ResponseEntity<Document> updateDocument(String orgKey, UUID documentId, DocumentInput documentInput) {
        return ResponseEntity.ok(toModel(updateDocument.execute(orgKey, documentId.toString(), documentInput)));
    }

    @Override
    public ResponseEntity<Document> updateDocumentProperties(String orgKey, UUID documentId,
                                                             DocumentPropertiesInput documentPropertiesInput) {
        return ResponseEntity.ok(toModel(
                updateDocumentProperties.execute(orgKey, documentId.toString(), documentPropertiesInput)));
    }

    @Override
    public ResponseEntity<Void> deleteDocument(String orgKey, UUID documentId) {
        deleteDocument.execute(orgKey, documentId.toString());
        return ResponseEntity.noContent().build();
    }

    // ── Translations ────────────────────────────────────────────

    @Override
    public ResponseEntity<List<DocumentTranslationSummary>> listDocumentTranslations(String orgKey, UUID documentId) {
        return ResponseEntity.ok(findDocumentTranslations.executeAll(orgKey, documentId.toString()).stream()
                .map(mapper::toTranslationSummaryModel)
                .toList());
    }

    @Override
    public ResponseEntity<DocumentTranslation> addDocumentTranslation(String orgKey, UUID documentId,
                                                                      DocumentTranslationInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toTranslationModel(
                addDocumentTranslation.execute(orgKey, documentId.toString(), input)));
    }

    @Override
    public ResponseEntity<DocumentTranslation> getDocumentTranslation(String orgKey, UUID documentId, String locale,
                                                                      Boolean draft) {
        return ResponseEntity.ok(mapper.toTranslationModel(findDocumentTranslations.executeOne(
                orgKey, documentId.toString(), locale, Boolean.TRUE.equals(draft))));
    }

    @Override
    public ResponseEntity<Void> removeDocumentTranslation(String orgKey, UUID documentId, String locale) {
        removeDocumentTranslation.execute(orgKey, documentId.toString(), locale);
        return ResponseEntity.noContent().build();
    }

    // ── Publishing ──────────────────────────────────────────────

    @Override
    public ResponseEntity<DocumentTranslation> publishDocumentTranslation(String orgKey, UUID documentId, String locale) {
        return ResponseEntity.ok(mapper.toTranslationModel(
                publishDocumentTranslation.execute(orgKey, documentId.toString(), locale)));
    }

    @Override
    public ResponseEntity<DocumentTranslation> unpublishDocumentTranslation(String orgKey, UUID documentId, String locale) {
        return ResponseEntity.ok(mapper.toTranslationModel(
                unpublishDocumentTranslation.execute(orgKey, documentId.toString(), locale)));
    }

    @Override
    public ResponseEntity<DocumentTranslation> discardDocumentDraft(String orgKey, UUID documentId, String locale) {
        return ResponseEntity.ok(mapper.toTranslationModel(
                discardDocumentDraft.execute(orgKey, documentId.toString(), locale)));
    }

    // ── Blocks ──────────────────────────────────────────────────

    @Override
    public ResponseEntity<DocumentBlock> appendDocumentBlock(String orgKey, UUID documentId, String locale,
                                                             DocumentBlockInput documentBlockInput) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toModel(
                appendDocumentBlock.execute(orgKey, documentId.toString(), locale, documentBlockInput)));
    }

    @Override
    public ResponseEntity<List<DocumentBlock>> reorderDocumentBlocks(String orgKey, UUID documentId, String locale,
                                                                     ReorderBlocksRequest reorderBlocksRequest) {
        return ResponseEntity.ok(reorderDocumentBlocks
                .execute(orgKey, documentId.toString(), locale, reorderBlocksRequest.getBlockIds()).stream()
                .map(mapper::toModel)
                .toList());
    }

    @Override
    public ResponseEntity<DocumentBlock> replaceDocumentBlock(String orgKey, UUID documentId, String locale,
                                                              String blockId, DocumentBlockInput documentBlockInput) {
        return ResponseEntity.ok(mapper.toModel(
                replaceDocumentBlock.execute(orgKey, documentId.toString(), locale, blockId, documentBlockInput)));
    }

    @Override
    public ResponseEntity<Void> deleteDocumentBlock(String orgKey, UUID documentId, String locale, String blockId) {
        deleteDocumentBlock.execute(orgKey, documentId.toString(), locale, blockId);
        return ResponseEntity.noContent().build();
    }

    // ── Public read ─────────────────────────────────────────────

    @Override
    public ResponseEntity<PublishedContent> getPublishedContent(String orgKey, String slug, String locale) {
        FindPublishedContent.PublishedContentView view = findPublishedContent.execute(orgKey, slug, locale);
        return ResponseEntity.ok(mapper.toPublishedContentModel(
                view.document(), view.served(), view.isFallback(), view.availableLocales()));
    }

    // ── Validation, import, export ──────────────────────────────

    @Override
    public ResponseEntity<ValidationResult> validateDocument(String orgKey, DocumentInput documentInput) {
        ValidateDocument.ValidationOutcome outcome = validateDocument.execute(documentInput);
        return ResponseEntity.ok(mapper.toModel(outcome.valid(), outcome.problems()));
    }

    @Override
    public ResponseEntity<ImportResult> importDocuments(String orgKey, MultipartFile file) {
        try {
            return ResponseEntity.ok(mapper.toModel(importDocuments.execute(orgKey, file.getInputStream())));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the uploaded document file", e);
        }
    }

    @Override
    public ResponseEntity<Resource> exportDocument(String orgKey, UUID documentId) {
        String yaml = exportDocument.execute(orgKey, documentId.toString());
        Resource body = new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + documentId + ".yaml\"")
                .contentType(MediaType.parseMediaType("application/x-yaml"))
                .body(body);
    }

    private Document toModel(DocumentDetails details) {
        return mapper.toModel(details.document(), details.selected(), details.states());
    }
}
