package com.processpuzzle.document.adapter.inbound;

import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentSignatureRepository;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.i18n.DocumentUiTranslationRepository;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes this feature's rows of an erased tenant: its published snapshots, drafts and documents,
 * its signatures and its UI translations.
 *
 * <p><b>{@code BEFORE_COMMIT}</b>, for the reason base-app's cleaner gives: these deletes join the
 * erasing transaction, so a tenant is either erased in every feature or in none. An
 * {@code AFTER_COMMIT} listener's writes would be discarded without an error.
 *
 * <p><b>Bulk statements, in {@code DeleteDocument}'s order</b>: published content, then drafts, then
 * the documents that give them meaning. There is no foreign key to force it, but it is the order the
 * module already commits to — content never outlives its document, even mid-transaction. A bulk
 * delete publishes no {@code DocumentUnpublished}: nothing subscribes to it today, and a
 * feature that does is receiving this same erasure for its own rows.
 *
 * <p><b>Nothing after commit.</b> Nothing of this feature lives outside the database: text and widget
 * blocks are a JSON column of the draft and published rows.
 *
 * <p>Named, because base-app and base-rule declare a {@code TenantDataCleaner} too, and an
 * application that assembles several features would otherwise have two beans of one name.
 */
@Component("documentTenantDataCleaner")
public class TenantDataCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(TenantDataCleaner.class);

    private final PublishedDocumentRepository published;
    private final DocumentDraftRepository drafts;
    private final DocumentRepository documents;
    private final DocumentUiTranslationRepository translations;
    private final DocumentSignatureRepository signatures;

    public TenantDataCleaner(PublishedDocumentRepository published, DocumentDraftRepository drafts,
                             DocumentRepository documents, DocumentUiTranslationRepository translations,
                             DocumentSignatureRepository signatures) {
        this.published = published;
        this.drafts = drafts;
        this.documents = documents;
        this.translations = translations;
        this.signatures = signatures;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrganizationDeleted(OrganizationDeletedEvent event) {
        String orgKey = event.orgKey();

        int erasedPublished = published.eraseByOrgKey(orgKey);
        int erasedDrafts = drafts.eraseByOrgKey(orgKey);
        int erasedDocuments = documents.eraseByOrgKey(orgKey);
        int erasedTranslations = translations.eraseByOrgKey(orgKey);
        int erasedSignatures = signatures.eraseByOrgKey(orgKey);

        LOG.info("Organization '{}' erased: {} document(s), {} draft(s), {} published version(s), {} "
                + "signature(s) and {} translation bundle(s) deleted.", orgKey, erasedDocuments, erasedDrafts,
                erasedPublished, erasedSignatures, erasedTranslations);
    }
}
