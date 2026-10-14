package com.processpuzzle.document.adapter.inbound;

import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseCandidate;
import com.processpuzzle.core.definition.ReleaseParticipant;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentInputPort;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentStatus;
import com.processpuzzle.document.domain.PublishedDocument;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Takes documents into a release without releasing them: a document is content, published translation by
 * translation by its own editors and publishers (see {@code PublishDocumentTranslation}), so a release
 * neither publishes nor withdraws one. It has nothing {@linkplain #pending pending} and its {@link #release}
 * does nothing.
 *
 * <p>What it adds is the check. An app route names a document by its slug, and the live application can only
 * show a document with a published translation, so only those are {@linkplain #referableKeys referable}: a
 * route to a document nobody has published refuses the release, as a route to a missing entity type does. A
 * document with unpublished changes is reported as a warning, so whoever releases knows that the live
 * application still shows its older text.
 */
@Component
public class DocumentReleaseParticipant implements ReleaseParticipant {

    static final String UNPUBLISHED_CHANGES = "document.unpublished-changes";

    private final DocumentRepository documents;
    private final DocumentDraftRepository drafts;
    private final PublishedDocumentRepository published;

    public DocumentReleaseParticipant(DocumentRepository documents, DocumentDraftRepository drafts,
                                      PublishedDocumentRepository published) {
        this.documents = documents;
        this.drafts = drafts;
        this.published = published;
    }

    @Override
    public String kind() {
        return DefinitionKinds.DOCUMENT;
    }

    @Override
    public int order() {
        return DefinitionKinds.DOCUMENT_ORDER;
    }

    @Override
    public Set<String> drafts(String orgKey) {
        return documents(orgKey).stream().map(Document::getId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** The id and the slug of every document with a published translation: what the live application can show. */
    @Override
    public Set<String> referableKeys(String orgKey) {
        Set<String> publishedIds = published.findByOrgKey(orgKey).stream()
                .map(PublishedDocument::getDocumentId)
                .collect(Collectors.toSet());
        Set<String> keys = new LinkedHashSet<>();
        for (Document document : documents(orgKey)) {
            if (publishedIds.contains(document.getId())) {
                keys.add(document.getId());
                if (document.getSlug() != null) {
                    keys.add(document.getSlug());
                }
            }
        }
        return keys;
    }

    /** Nothing: a document's translations are published on their own, never by a release. */
    @Override
    public List<PendingChange> pending(String orgKey) {
        return List.of();
    }

    /**
     * The entity types a document's input ports are bound to. Optional, as base-document only records that a
     * port exists and the host resolves its binding: a port of a missing type shows nothing rather than failing.
     */
    @Override
    public List<DefinitionReference> references(String orgKey) {
        List<DefinitionReference> references = new ArrayList<>();
        for (Document document : documents(orgKey)) {
            document.getPorts().inputPorts().stream()
                    .map(DocumentInputPort::entityType)
                    .filter(type -> type != null && !type.isBlank())
                    .distinct()
                    .forEach(type -> references.add(
                            DefinitionReference.optional(kind(), document.getId(), DefinitionKinds.ENTITY, type)));
        }
        return references;
    }

    /** A warning for every document with a translation whose draft is not what readers are served. */
    @Override
    public List<ReleaseProblem> check(String orgKey, ReleaseCandidate candidate) {
        Map<String, Map<String, Long>> publishedRevisions = published.findByOrgKey(orgKey).stream()
                .collect(Collectors.groupingBy(PublishedDocument::getDocumentId,
                        Collectors.toMap(PublishedDocument::getLocale, PublishedDocument::getPublishedRevision)));
        Map<String, List<DocumentDraft>> draftsByDocument = drafts.findByOrgKey(orgKey).stream()
                .collect(Collectors.groupingBy(DocumentDraft::getDocumentId));

        List<ReleaseProblem> problems = new ArrayList<>();
        for (Document document : documents(orgKey)) {
            Map<String, Long> revisions = publishedRevisions.getOrDefault(document.getId(), Map.of());
            List<String> unpublished = draftsByDocument.getOrDefault(document.getId(), List.of()).stream()
                    .filter(draft -> DocumentStatus.derive(draft.getRevision(), revisions.get(draft.getLocale())) != DocumentStatus.PUBLISHED)
                    .map(DocumentDraft::getLocale)
                    .sorted()
                    .toList();
            if (!unpublished.isEmpty()) {
                problems.add(ReleaseProblem.warning(kind(), document.getId(), UNPUBLISHED_CHANGES,
                        "The document '" + nameOf(document) + "' has unpublished changes (" + String.join(", ", unpublished)
                                + "). A release does not publish documents: publish its translations in the document editor."));
            }
        }
        return problems;
    }

    /** Nothing to do: see {@link #pending}. */
    @Override
    public void release(String orgKey) {
        // Documents are published translation by translation, never by a release.
    }

    private List<Document> documents(String orgKey) {
        return documents.findByOrgKey(orgKey).stream().sorted(Comparator.comparing(Document::getId)).toList();
    }

    private static String nameOf(Document document) {
        return document.getTitle() == null || document.getTitle().isBlank() ? document.getSlug() : document.getTitle();
    }
}
