package com.processpuzzle.document.usecase;

import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentSignature;
import com.processpuzzle.document.domain.DocumentSignatureRepository;
import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import com.processpuzzle.document.usecase.exception.DocumentFileStoreUnavailableException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentPublishingConflictException;
import com.processpuzzle.document.usecase.port.DocumentFileStore;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Keeps the PDF of a signed report on its signature.
 *
 * <p>Signing is two-phase on purpose: the stamp in the PDF shows what the server recorded (signer, time,
 * signature id), so the signature exists before the PDF can. This is the second phase.
 *
 * <p><b>Only the signer attaches</b>, identified as {@link SignDocument} identifies them — anybody else
 * could otherwise put a file of their choosing under someone's signature. <b>Once</b>: a PDF already
 * attached is a 409, and a failed upload leaves the signature without one, so the attempt can be
 * repeated. <b>Only a PDF</b>, within {@link #MAX_BYTES}; the content is checked by its magic number,
 * not by the declared type alone. The SHA-256 of the bytes is recorded next to the object.
 */
@Service
@Transactional
public class AttachSignedPdf {

    public static final String PDF = "application/pdf";
    public static final int MAX_BYTES = 20 * 1024 * 1024;
    private static final byte[] MAGIC = {'%', 'P', 'D', 'F'};

    private final DocumentRepository repository;
    private final DocumentSignatureRepository signatureRepository;
    private final DocumentGuard guard;
    private final ObjectProvider<CurrentPrincipal> currentPrincipal;
    private final ObjectProvider<DocumentFileStore> fileStore;

    public AttachSignedPdf(DocumentRepository repository, DocumentSignatureRepository signatureRepository,
                           DocumentGuard guard, ObjectProvider<CurrentPrincipal> currentPrincipal,
                           ObjectProvider<DocumentFileStore> fileStore) {
        this.repository = repository;
        this.signatureRepository = signatureRepository;
        this.guard = guard;
        this.currentPrincipal = currentPrincipal;
        this.fileStore = fileStore;
    }

    public DocumentSignature execute(String orgKey, String documentId, String signatureId, byte[] content,
                                     String fileName, String mimeType) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));
        if (!guard.canRead(document)) {
            throw new DocumentNotFoundException(orgKey, documentId);
        }
        guard.requireOrganizationAccess(orgKey);
        DocumentSignature signature = signatureRepository.findByOrgKeyAndIdAndDocumentId(orgKey, signatureId, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));
        SignDocument.Signer signer = SignDocument.signerOf(guard, currentPrincipal, orgKey)
                .orElseThrow(() -> new DocumentAccessDeniedException(
                        "There is no authenticated member of '" + orgKey + "' to attach a PDF as"));
        if (!signer.is(signature)) {
            throw new DocumentAccessDeniedException("Only the signer may attach the PDF of signature '" + signatureId + "'");
        }
        if (signature.hasPdf()) {
            throw DocumentPublishingConflictException.pdfAlreadyAttached(signatureId);
        }
        validate(content, mimeType);

        DocumentFileStore store = fileStore.getIfUnique();
        if (store == null) {
            throw new DocumentFileStoreUnavailableException("No file store is configured for signed documents.");
        }
        String name = fileName == null || fileName.isBlank() ? signatureId + ".pdf" : fileName;
        DocumentFileStore.StoredFile stored = store.store(content, name, PDF);
        signature.attachPdf(stored.bucket(), stored.objectId(), name, sha256(content), Instant.now());
        return signatureRepository.save(signature);
    }

    private static void validate(byte[] content, String mimeType) {
        if (mimeType == null || !PDF.equalsIgnoreCase(mimeType.split(";")[0].trim())) {
            throw new IllegalArgumentException("Only " + PDF + " can be attached to a signature");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("The PDF is empty");
        }
        if (content.length > MAX_BYTES) {
            throw new IllegalArgumentException("The PDF exceeds the limit of " + MAX_BYTES + " bytes");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (content[i] != MAGIC[i]) {
                throw new IllegalArgumentException("The file is not a PDF");
            }
        }
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", absent);
        }
    }
}
