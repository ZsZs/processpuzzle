package com.processpuzzle.document.usecase.service;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.PublishedDocument;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.usecase.DocumentTranslationView;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pairs a document's drafts with its published snapshots so callers get
 * {@link DocumentTranslationView}s rather than two half-answers to reconcile themselves.
 *
 * <p>Also the single place that resolves the <em>source</em> locale's revision, which every
 * translation's staleness is measured against. Doing it once per document matters: naively, each
 * translation would look the source up for itself, turning one read into one per locale.
 */
@Component
public class DocumentTranslationAssembler {

    private final DocumentDraftRepository draftRepository;
    private final PublishedDocumentRepository publishedRepository;

    public DocumentTranslationAssembler(DocumentDraftRepository draftRepository,
                                        PublishedDocumentRepository publishedRepository) {
        this.draftRepository = draftRepository;
        this.publishedRepository = publishedRepository;
    }

    /** Every locale this document exists in, state only, ordered so the source locale comes first. */
    public List<DocumentTranslationView> statesOf(Document document) {
        List<DocumentDraft> drafts = draftRepository.findByOrgKeyAndDocumentId(document.getOrgKey(), document.getId());
        Map<String, PublishedDocument> published = publishedByLocale(document);
        Long sourceRevision = sourceRevisionOf(document, drafts);

        List<DocumentTranslationView> views = new ArrayList<>();
        for (DocumentDraft draft : drafts) {
            views.add(DocumentTranslationView.stateOnly(draft, published.get(draft.getLocale()), sourceRevision));
        }
        views.sort(sourceFirst(document.getSourceLocale()));
        return views;
    }

    /**
     * One locale's content, draft or published.
     *
     * <p>Empty means "nothing to serve": either the locale has no translation at all, or
     * {@code draft} was false and it has never been published. The two are deliberately not
     * distinguished here — a caller asking for published content has no use for the news that a
     * draft exists, and the endpoints that do care ask the repositories directly.
     */
    public Optional<DocumentTranslationView> contentOf(Document document, String locale, boolean draft) {
        Optional<DocumentDraft> theDraft =
                draftRepository.findByOrgKeyAndDocumentIdAndLocale(document.getOrgKey(), document.getId(), locale);
        Optional<PublishedDocument> theSnapshot =
                publishedRepository.findByOrgKeyAndDocumentIdAndLocale(document.getOrgKey(), document.getId(), locale);
        Long sourceRevision = sourceRevisionOf(document);

        if (draft) {
            return theDraft.map(d -> DocumentTranslationView.ofDraft(d, theSnapshot.orElse(null), sourceRevision));
        }
        return theSnapshot.map(p -> DocumentTranslationView.ofPublished(p, theDraft.orElse(null), sourceRevision));
    }

    /** The locales of this document that have a published snapshot. Source locale first. */
    public List<String> publishedLocalesOf(Document document) {
        return publishedRepository.findByOrgKeyAndDocumentId(document.getOrgKey(), document.getId()).stream()
                .map(PublishedDocument::getLocale)
                .sorted(Comparator.comparing((String locale) -> !locale.equals(document.getSourceLocale()))
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    public Long sourceRevisionOf(Document document) {
        return draftRepository
                .findByOrgKeyAndDocumentIdAndLocale(document.getOrgKey(), document.getId(), document.getSourceLocale())
                .map(DocumentDraft::getRevision)
                .orElse(null);
    }

    private static Long sourceRevisionOf(Document document, List<DocumentDraft> drafts) {
        return drafts.stream()
                .filter(draft -> draft.getLocale().equals(document.getSourceLocale()))
                .map(DocumentDraft::getRevision)
                .findFirst()
                .orElse(null);
    }

    private Map<String, PublishedDocument> publishedByLocale(Document document) {
        Map<String, PublishedDocument> byLocale = new HashMap<>();
        for (PublishedDocument snapshot
                : publishedRepository.findByOrgKeyAndDocumentId(document.getOrgKey(), document.getId())) {
            byLocale.put(snapshot.getLocale(), snapshot);
        }
        return byLocale;
    }

    /**
     * Source locale first, then alphabetical. The order is not cosmetic: the design UI's locale
     * selector opens on the first entry, and the source language is what an editor overwhelmingly
     * wants when they open a document without asking for a language.
     */
    private static Comparator<DocumentTranslationView> sourceFirst(String sourceLocale) {
        return Comparator
                .comparing((DocumentTranslationView view) -> !view.locale().equals(sourceLocale))
                .thenComparing(DocumentTranslationView::locale);
    }
}
