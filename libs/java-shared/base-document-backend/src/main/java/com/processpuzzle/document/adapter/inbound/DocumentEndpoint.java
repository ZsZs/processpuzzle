package com.processpuzzle.document.adapter.inbound;

import com.processpuzzle.document.api.BaseDocumentApi;
import com.processpuzzle.document.model.*;
import com.processpuzzle.document.usecase.*;
import com.processpuzzle.core.logging.LogClass;
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

@RestController
@LogClass
public class DocumentEndpoint implements BaseDocumentApi {

    private final CreateDocument createDocument;
    private final UpdateDocument updateDocument;
    private final UpdateDocumentProperties updateDocumentProperties;
    private final DeleteDocument deleteDocument;
    private final FindDocument findDocument;
    private final FindAllDocuments findAllDocuments;
    private final AppendDocumentBlock appendDocumentBlock;
    private final ReplaceDocumentBlock replaceDocumentBlock;
    private final DeleteDocumentBlock deleteDocumentBlock;
    private final ReorderDocumentBlocks reorderDocumentBlocks;
    private final ValidateDocument validateDocument;
    private final ImportDocuments importDocuments;
    private final ExportDocument exportDocument;
    private final DocumentMapper mapper;

    public DocumentEndpoint(CreateDocument createDocument,
                             UpdateDocument updateDocument,
                             UpdateDocumentProperties updateDocumentProperties,
                             DeleteDocument deleteDocument,
                             FindDocument findDocument,
                             FindAllDocuments findAllDocuments,
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
        this.appendDocumentBlock = appendDocumentBlock;
        this.replaceDocumentBlock = replaceDocumentBlock;
        this.deleteDocumentBlock = deleteDocumentBlock;
        this.reorderDocumentBlocks = reorderDocumentBlocks;
        this.validateDocument = validateDocument;
        this.importDocuments = importDocuments;
        this.exportDocument = exportDocument;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<Document> createDocument(String orgKey, DocumentInput input) {
        var created = createDocument.execute(orgKey, input);
        return new ResponseEntity<>(mapper.toModel(created), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<Document> updateDocument(String orgKey, String documentId, DocumentInput input) {
        var updated = updateDocument.execute(orgKey, documentId, input);
        return ResponseEntity.ok(mapper.toModel(updated));
    }

    @Override
    public ResponseEntity<Document> updateDocumentProperties(
            String orgKey, String documentId, DocumentPropertiesInput input) {
        var updated = updateDocumentProperties.execute(orgKey, documentId, input);
        return ResponseEntity.ok(mapper.toModel(updated));
    }

    @Override
    public ResponseEntity<Void> deleteDocument(String orgKey, String documentId) {
        deleteDocument.execute(orgKey, documentId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Document> getDocument(String orgKey, String documentId) {
        return ResponseEntity.ok(mapper.toModel(findDocument.execute(orgKey, documentId)));
    }

    @Override
    public ResponseEntity<PageOfDocumentSummary> listDocuments(
            String orgKey, String where, String order, Integer page, Integer size) {
        return ResponseEntity.ok(mapper.toModel(findAllDocuments.execute(orgKey, where, order, page, size)));
    }

    @Override
    public ResponseEntity<DocumentBlock> appendDocumentBlock(
            String orgKey, String documentId, DocumentBlockInput input) {
        var created = appendDocumentBlock.execute(orgKey, documentId, input);
        return new ResponseEntity<>(mapper.toModel(created), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<DocumentBlock> replaceDocumentBlock(
            String orgKey, String documentId, String blockId, DocumentBlockInput input) {
        var replaced = replaceDocumentBlock.execute(orgKey, documentId, blockId, input);
        return ResponseEntity.ok(mapper.toModel(replaced));
    }

    @Override
    public ResponseEntity<Void> deleteDocumentBlock(String orgKey, String documentId, String blockId) {
        deleteDocumentBlock.execute(orgKey, documentId, blockId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<DocumentBlock>> reorderDocumentBlocks(
            String orgKey, String documentId, ReorderBlocksRequest request) {
        var reordered = reorderDocumentBlocks.execute(orgKey, documentId, request.getBlockIds());
        return ResponseEntity.ok(reordered.stream().map(mapper::toModel).toList());
    }

    @Override
    public ResponseEntity<ValidationResult> validateDocument(String orgKey, DocumentInput input) {
        var outcome = validateDocument.execute(input);
        return ResponseEntity.ok(mapper.toModel(outcome.valid(), outcome.problems()));
    }

    @Override
    public ResponseEntity<ImportResult> importDocuments(String orgKey, MultipartFile file) {
        try {
            var outcome = importDocuments.execute(orgKey, file.getInputStream());
            return ResponseEntity.ok(mapper.toModel(outcome));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ResponseEntity<Resource> exportDocument(String orgKey, String documentId) {
        String yaml = exportDocument.execute(orgKey, documentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + documentId + ".yaml\"")
                .contentType(MediaType.parseMediaType("application/x-yaml"))
                .body(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}
