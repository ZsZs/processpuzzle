package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.PublishedDocument;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * The public read: a document's published content, by slug.
 *
 * <p>This class holds no reference to {@code DocumentDraftRepository} and does not use
 * {@code DocumentTranslationAssembler}, which does. That is the enforcement mechanism for "a draft
 * is never publicly readable" — not a flag or a filter, but the absence of any collaborator that
 * could reach one. Keep it that way: adding a draft repository here would quietly turn a structural
 * guarantee back into a convention.
 *
 * <p>Denials answer 404 rather than 403, and a document with no published locale answers 404 too,
 * so an unauthenticated caller cannot tell "does not exist" from "exists but is not for you" — the
 * distinction is exactly what a private document is meant to withhold.
 */
@Service
@Transactional(readOnly = true)
public class FindPublishedContent {

    private final DocumentRepository repository;
    private final PublishedDocumentRepository publishedRepository;
    private final DocumentGuard guard;

    public FindPublishedContent(DocumentRepository repository,
                                PublishedDocumentRepository publishedRepository,
                                DocumentGuard guard) {
        this.repository = repository;
        this.publishedRepository = publishedRepository;
        this.guard = guard;
    }

    /**
     * @param requestedLocale the reader's language; the document's {@code sourceLocale} when null
     */
    public PublishedContentView execute(String orgKey, String slug, String requestedLocale) {
        Document document = repository.findByOrgKeyAndSlug(orgKey, slug)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, slug));
        if (!guard.canRead(document)) {
            throw new DocumentNotFoundException(orgKey, slug);
        }

        List<PublishedDocument> snapshots = publishedRepository.findByOrgKeyAndDocumentId(orgKey, document.getId());
        if (snapshots.isEmpty()) {
            throw new DocumentNotFoundException(orgKey, slug);
        }

        String wanted = requestedLocale == null ? document.getSourceLocale() : requestedLocale;
        Optional<PublishedDocument> exact = snapshots.stream()
                .filter(snapshot -> snapshot.getLocale().equals(wanted))
                .findFirst();

        // Falling back to the source locale rather than 404ing: a reader whose language is not
        // translated yet is better served the original with a notice than an empty page, and
        // isFallback is what lets the UI say so instead of appearing to have no content.
        PublishedDocument served = exact.orElseGet(() -> snapshots.stream()
                .filter(snapshot -> snapshot.getLocale().equals(document.getSourceLocale()))
                .findFirst()
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, slug)));

        List<String> availableLocales = snapshots.stream()
                .map(PublishedDocument::getLocale)
                .sorted()
                .toList();

        // Null draft, deliberately: the view is built from the snapshot alone, so no draft revision
        // or staleness leaks into an anonymous response.
        return new PublishedContentView(
                document,
                DocumentTranslationView.ofPublished(served, null, null),
                exact.isEmpty(),
                availableLocales);
    }

    /**
     * @param isFallback true when the requested locale had no published snapshot and the source
     *                   locale was served instead
     */
    public record PublishedContentView(Document document,
                                       DocumentTranslationView served,
                                       boolean isFallback,
                                       List<String> availableLocales) {

        public PublishedContentView {
            availableLocales = availableLocales == null ? List.of() : List.copyOf(availableLocales);
        }
    }
}
