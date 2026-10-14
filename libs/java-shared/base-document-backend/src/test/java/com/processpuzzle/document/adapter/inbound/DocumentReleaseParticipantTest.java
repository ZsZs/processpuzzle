package com.processpuzzle.document.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.ReleaseCandidate;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentInputPort;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentRoles;
import com.processpuzzle.document.domain.PortType;
import com.processpuzzle.document.domain.PublishedDocument;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DocumentReleaseParticipantTest {

    private static final String ORG = "acme";

    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final DocumentDraftRepository drafts = mock(DocumentDraftRepository.class);
    private final PublishedDocumentRepository published = mock(PublishedDocumentRepository.class);
    private final DocumentReleaseParticipant participant = new DocumentReleaseParticipant(documents, drafts, published);

    private final Document guide = new Document(ORG, "doc-1", "guide", "Guide", "en", "ada");
    private final Document notes = new Document(ORG, "doc-2", "notes", "Notes", "en", "ada");

    @BeforeEach
    void setUp() {
        when(documents.findByOrgKey(ORG)).thenReturn(List.of(notes, guide));
    }

    @Test
    void takesPartAsDocuments() {
        assertThat(participant.kind()).isEqualTo(DefinitionKinds.DOCUMENT);
        assertThat(participant.order()).isEqualTo(DefinitionKinds.DOCUMENT_ORDER);
        assertThat(participant.drafts(ORG)).containsExactly("doc-1", "doc-2");
    }

    @Test
    void onlyAPublishedDocumentCanBeReferredTo_byItsIdOrItsSlug() {
        when(published.findByOrgKey(ORG)).thenReturn(List.of(snapshot("doc-1", "en", 1L)));

        assertThat(participant.referableKeys(ORG)).containsExactly("doc-1", "guide");
    }

    @Test
    void neverHasAnythingPendingNorReleasesAnything() {
        assertThat(participant.pending(ORG)).isEmpty();

        participant.release(ORG);

        verifyNoInteractions(drafts, published);
    }

    @Test
    void refersOptionallyToTheEntityTypesItsPortsAreBoundTo() {
        guide.replaceProperties("guide", "Guide", null, null, null, "en", false, DocumentRoles.unrestricted(),
                new DocumentPorts(List.of(
                        new DocumentInputPort("customer", PortType.ENTITY_REF, true, null, null, "Customer", null, null),
                        new DocumentInputPort("customers", PortType.ENTITY_COLLECTION, false, null, null, "Customer", null, null),
                        new DocumentInputPort("greeting", PortType.STRING, false, null, null, null, null, null)),
                        List.of()));

        assertThat(participant.references(ORG))
                .containsExactly(DefinitionReference.optional(DefinitionKinds.DOCUMENT, "doc-1", DefinitionKinds.ENTITY, "Customer"));
    }

    @Test
    void warnsAboutEveryDocumentWithUnpublishedChanges() {
        DocumentDraft guideEn = draft("doc-1", "en", 2);
        DocumentDraft guideDe = draft("doc-1", "de", 1);
        DocumentDraft notesEn = draft("doc-2", "en", 1);
        when(drafts.findByOrgKey(ORG)).thenReturn(List.of(guideEn, guideDe, notesEn));
        // guide/en was edited since it was published, guide/de never published, notes/en current.
        when(published.findByOrgKey(ORG)).thenReturn(List.of(snapshot("doc-1", "en", 1L), snapshot("doc-2", "en", 1L)));

        List<ReleaseProblem> problems = participant.check(ORG, mock(ReleaseCandidate.class));

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.severity()).isEqualTo(ReleaseProblem.Severity.WARNING);
            assertThat(problem.key()).isEqualTo("doc-1");
            assertThat(problem.errorId()).isEqualTo(DocumentReleaseParticipant.UNPUBLISHED_CHANGES);
            assertThat(problem.message()).contains("'Guide'", "(de, en)");
        });
    }

    private static DocumentDraft draft(String documentId, String locale, int edits) {
        DocumentDraft draft = new DocumentDraft(ORG, documentId, locale, DocumentContent.empty(), null);
        for (int edit = 1; edit < edits; edit++) {
            draft.replaceBlocks(List.of());
        }
        return draft;
    }

    private static PublishedDocument snapshot(String documentId, String locale, Long revision) {
        return new PublishedDocument(ORG, documentId, locale, DocumentContent.empty(), revision, Instant.now(), "ada");
    }
}
