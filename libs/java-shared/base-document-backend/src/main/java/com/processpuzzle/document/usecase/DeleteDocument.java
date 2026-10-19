package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.ContentBook;
import com.processpuzzle.document.domain.ContentBookRepository;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentKey;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import com.processpuzzle.document.usecase.service.DocumentPlatformFacts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes a document with every translation, draft and published alike.
 *
 * <p>The cascade is explicit rather than a JPA one, for the reason {@code Organization} documents
 * for its own children: the associations would have to join on composite key pairs for no benefit.
 * Doing it here in one transaction also makes the order visible — content first, then the record
 * that gives it meaning — so a failure part-way cannot leave published content addressable by a
 * slug whose document is gone.
 *
 * <p>Every content book of the organization that shows the document loses that node; the pages filed
 * under it move up into its place rather than disappearing with it. Not guarded by the books' own
 * editor roles: the document's editor is deleting the document, and a book cannot keep a document
 * alive by pointing at it.
 */
@Service
@Transactional
public class DeleteDocument {

    private final DocumentRepository repository;
    private final DocumentDraftRepository draftRepository;
    private final PublishedDocumentRepository publishedRepository;
    private final ContentBookRepository contentBookRepository;
    private final DocumentGuard guard;
    private final DocumentPlatformFacts facts;

    public DeleteDocument(DocumentRepository repository,
                          DocumentDraftRepository draftRepository,
                          PublishedDocumentRepository publishedRepository,
                          ContentBookRepository contentBookRepository,
                          DocumentGuard guard,
                          DocumentPlatformFacts facts) {
        this.repository = repository;
        this.draftRepository = draftRepository;
        this.publishedRepository = publishedRepository;
        this.contentBookRepository = contentBookRepository;
        this.guard = guard;
        this.facts = facts;
    }

    public void execute(String orgKey, String documentId) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));
        guard.requireEditor(document);

        publishedRepository.deleteByOrgKeyAndDocumentId(orgKey, documentId);
        draftRepository.deleteByOrgKeyAndDocumentId(orgKey, documentId);
        for (ContentBook book : contentBookRepository.findByOrgKeyReferencing(orgKey, documentId)) {
            book.replaceNodes(book.getNodes().detach(documentId));
            contentBookRepository.save(book);
        }
        repository.deleteById(new DocumentKey(orgKey, documentId));
        facts.deleted(document);
    }
}
