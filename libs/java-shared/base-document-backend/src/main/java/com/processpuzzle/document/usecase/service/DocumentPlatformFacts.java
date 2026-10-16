package com.processpuzzle.document.usecase.service;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentStatus;
import com.processpuzzle.document.domain.PublishedDocument;
import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes base-document's {@link PlatformEvent}s — the raw facts base-event matches against the
 * organization's catalog, so a workflow can start on a new document or wait for one to be published.
 *
 * <p>A document's state is the {@link DocumentStatus} of its <em>source-locale</em> translation, the
 * default language. Status is per translation and derived, so there is no stored field to watch: a
 * use case computes the status before and after its write and hands both here, and a fact is
 * published only when the two differ.
 */
@Component
public class DocumentPlatformFacts {

    /** The {@code subjectType} of every fact published here. */
    public static final String SUBJECT_TYPE = "document";

    private final ApplicationEventPublisher events;

    public DocumentPlatformFacts(ApplicationEventPublisher events) {
        this.events = events;
    }

    public void created(Document document) {
        events.publishEvent(PlatformEvent.of(document.getOrgKey(), SUBJECT_TYPE, document.getId(),
                PlatformEventAction.CREATED, payloadOf(document), Instant.now()));
    }

    public void deleted(Document document) {
        events.publishEvent(PlatformEvent.of(document.getOrgKey(), SUBJECT_TYPE, document.getId(),
                PlatformEventAction.DELETED, null, Instant.now()));
    }

    /**
     * Publishes {@code STATE_CHANGED} when the default translation's status moved. Either side may be
     * null — no source-locale draft — and null to null is no change.
     */
    public void stateChanged(Document document, DocumentStatus before, DocumentStatus after) {
        if (after == null || after == before) {
            return;
        }
        events.publishEvent(PlatformEvent.stateChanged(document.getOrgKey(), SUBJECT_TYPE, document.getId(),
                after.name(), payloadOf(document), Instant.now()));
    }

    /** The status of {@code document}'s source-locale translation among already loaded rows, or null. */
    public static DocumentStatus defaultStatusOf(Document document,
                                                 Collection<DocumentDraft> drafts,
                                                 Collection<PublishedDocument> snapshots) {
        String locale = document.getSourceLocale();
        Long publishedRevision = snapshots.stream()
                .filter(snapshot -> locale.equals(snapshot.getLocale()))
                .findFirst()
                .map(PublishedDocument::getPublishedRevision)
                .orElse(null);
        return drafts.stream()
                .filter(draft -> locale.equals(draft.getLocale()))
                .findFirst()
                .map(draft -> DocumentStatus.derive(draft.getRevision(), publishedRevision))
                .orElse(null);
    }

    private static Map<String, Object> payloadOf(Document document) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("slug", document.getSlug());
        payload.put("title", document.getTitle());
        payload.put("sourceLocale", document.getSourceLocale());
        return payload;
    }
}
