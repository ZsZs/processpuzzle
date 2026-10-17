package com.processpuzzle.document.usecase;

import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentContentConverter;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentSignature;
import com.processpuzzle.document.domain.DocumentSignatureRepository;
import com.processpuzzle.document.domain.PublishedDocument;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentPublishingConflictException;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import com.processpuzzle.document.usecase.service.DocumentPlatformFacts;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Records that the current principal accepts one locale's published content, for one object — see
 * {@link DocumentSignature} for what is recorded and why.
 *
 * <p><b>Only published content is signable.</b> The snapshot is what a reader is shown; a draft can
 * change under the signer between reading and signing, and nobody else could ever see what they signed.
 *
 * <p><b>The signer comes from the security context, never from the request.</b> The access policy's
 * principal is preferred when the deploying application supplies one; otherwise the token's display
 * name, through {@link CurrentPrincipal}. With neither there is nobody to sign as, which is a 403 rather
 * than an anonymous signature.
 *
 * <p><b>Only a member signs, even a public document.</b> Reading follows {@link DocumentGuard#canRead},
 * where {@code isPublic} lets anyone in; signing does not, because a signature publishes a fact into the
 * organization's event stream that can advance its workflows. A token of another realm is a 403.
 *
 * <p>What this cannot check is the subject: this module knows nothing of base-entity, so whether the
 * signer is the person {@code subjectId} names is the calling workflow's business — it hands the task
 * to the right role.
 *
 * <p><b>Signing twice is answered, not repeated.</b> The same signer, object, locale and revision return
 * the existing signature with {@link Outcome#created()} false, and publish no second fact — a
 * double-clicked button must not advance a workflow twice.
 */
@Service
@Transactional
public class SignDocument {

    private final DocumentRepository repository;
    private final PublishedDocumentRepository publishedRepository;
    private final DocumentSignatureRepository signatureRepository;
    private final DocumentGuard guard;
    private final DocumentPlatformFacts facts;
    private final ObjectProvider<CurrentPrincipal> currentPrincipal;

    public SignDocument(DocumentRepository repository,
                        PublishedDocumentRepository publishedRepository,
                        DocumentSignatureRepository signatureRepository,
                        DocumentGuard guard,
                        DocumentPlatformFacts facts,
                        ObjectProvider<CurrentPrincipal> currentPrincipal) {
        this.repository = repository;
        this.publishedRepository = publishedRepository;
        this.signatureRepository = signatureRepository;
        this.guard = guard;
        this.facts = facts;
        this.currentPrincipal = currentPrincipal;
    }

    /** @param requestedLocale the locale read; the document's {@code sourceLocale} when null */
    public Outcome execute(String orgKey, String documentId, String subjectType, String subjectId,
                           String requestedLocale) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));
        if (!guard.canRead(document)) {
            throw new DocumentNotFoundException(orgKey, documentId);
        }
        guard.requireOrganizationAccess(orgKey);
        String signer = signer(orgKey)
                .orElseThrow(() -> new DocumentAccessDeniedException(
                        "There is no authenticated member of '" + orgKey + "' to sign document '" + documentId
                                + "' as"));

        String locale = requestedLocale == null ? document.getSourceLocale() : requestedLocale;
        PublishedDocument snapshot = publishedRepository.findByOrgKeyAndDocumentIdAndLocale(orgKey, documentId, locale)
                .orElseThrow(() -> DocumentPublishingConflictException.notPublished(documentId, locale));

        Optional<DocumentSignature> existing = signatureRepository
                .findFirstByOrgKeyAndDocumentIdAndLocaleAndRevisionAndSubjectTypeAndSubjectIdAndSignedBy(
                        orgKey, documentId, locale, snapshot.getPublishedRevision(), subjectType, subjectId, signer);
        if (existing.isPresent()) {
            return new Outcome(existing.get(), false);
        }

        DocumentSignature signature = signatureRepository.save(new DocumentSignature(
                UUID.randomUUID().toString(), orgKey, documentId, document.getTitle(), locale,
                snapshot.getPublishedRevision(), hashOf(snapshot), subjectType, subjectId, signer, Instant.now()));
        facts.signed(document, signature);
        return new Outcome(signature, true);
    }

    private Optional<String> signer(String orgKey) {
        String fromPolicy = guard.currentPrincipal();
        if (fromPolicy != null && !fromPolicy.isBlank()) {
            return Optional.of(fromPolicy);
        }
        CurrentPrincipal principal = currentPrincipal.getIfAvailable();
        if (principal == null || !principal.isMemberOf(orgKey)) {
            return Optional.empty();
        }
        return principal.displayName();
    }

    /** Hex SHA-256 of the block list exactly as it is stored, so the hash can be recomputed from the row. */
    static String hashOf(PublishedDocument snapshot) {
        String stored = new DocumentContentConverter().convertToDatabaseColumn(snapshot.getContent());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(stored.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", absent);
        }
    }

    /** @param created false when the same signature already existed and was returned as it was */
    public record Outcome(DocumentSignature signature, boolean created) {
    }
}
