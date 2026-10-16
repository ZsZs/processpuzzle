package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.BlockKind;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentRoles;
import com.processpuzzle.document.domain.PublishedDocument;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImportDocumentCommandsTest {
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final DocumentDraftRepository drafts = mock(DocumentDraftRepository.class);
    private final PublishedDocumentRepository publications = mock(PublishedDocumentRepository.class);

    @BeforeEach
    void saveReturnsDocument() {
        when(documents.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void organizationAccessPrecedesValidationAndRepositoryAccess() {
        assertThatThrownBy(() -> importer(TestPolicies.outsider()).execute("demo", List.of(candidate(null))))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(drafts, publications);
        verify(documents, never()).findByOrgKeyAndSlug(any(), any());
        verify(documents, never()).save(any());
    }

    @Test
    void everyStoredEditorPolicyIsCheckedBeforeReplacingAnyMetadata() {
        Document allowed = stored("allowed", "editor");
        Document denied = stored("denied", "other-editor");
        when(documents.findByOrgKeyAndSlug("demo", "allowed")).thenReturn(Optional.of(allowed));
        when(documents.findByOrgKeyAndSlug("demo", "denied")).thenReturn(Optional.of(denied));

        assertThatThrownBy(() -> importer(TestPolicies.holding("editor")).execute("demo",
                List.of(candidate("new"), candidate("allowed"), candidate("denied"))))
                .isInstanceOf(DocumentAccessDeniedException.class);

        assertThat(allowed.getTitle()).isEqualTo("Original");
        assertThat(denied.getRoles().editorRoles()).containsExactly("other-editor");
        verify(documents, never()).save(any());
        verifyNoInteractions(drafts, publications);
    }

    @Test
    void anInvalidLaterCandidatePreventsEveryWrite() {
        var broken = new DocumentCommand(candidate("broken").properties(),
                List.of(new DocumentTranslationCommand("de", contentBoundToMissingPort())));

        var result = importer(TestPolicies.permitAll()).execute("demo", List.of(candidate("new"), broken));

        assertThat(result.errors()).singleElement().asString().contains("broken", "de", "unknown-port");
        assertThat(result.created()).isZero();
        verify(documents, never()).save(any());
        verifyNoInteractions(drafts, publications);
    }

    @Test
    void retainedDraftsAndPublishedSnapshotsBothGateTheWholeBatch() {
        Document existing = stored("existing", "editor");
        when(documents.findByOrgKeyAndSlug("demo", "existing")).thenReturn(Optional.of(existing));
        when(drafts.findByOrgKeyAndDocumentId("demo", "existing")).thenReturn(List.of(
                new DocumentDraft("demo", "existing", "de", contentBoundToMissingPort(), null)));
        when(publications.findByOrgKeyAndDocumentId("demo", "existing")).thenReturn(List.of(
                new PublishedDocument("demo", "existing", "en", contentBoundToMissingPort(), 1L, Instant.now(), "ada")));

        var result = importer(TestPolicies.permitAll()).execute("demo",
                List.of(candidate("new"), candidate("existing")));

        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("Published content", "en"));
        assertThat(existing.getTitle()).isEqualTo("Original");
        verify(documents, never()).save(any());
        verify(drafts, never()).save(any());
        verify(publications, never()).save(any());
    }

    @Test
    void omittedContentCreatesBlankDraftsAndNeverPublishes() {
        var command = new DocumentCommand(candidate("new").properties(),
                List.of(new DocumentTranslationCommand("de", null)));

        var result = importer(TestPolicies.principal("ada")).execute("demo", List.of(command));

        assertThat(result).isEqualTo(new ImportOutcome(1, 0, List.of()));
        var saved = org.mockito.ArgumentCaptor.forClass(Document.class);
        verify(documents).save(saved.capture());
        assertThat(saved.getValue().getAuthor()).isEqualTo("ada");
        var content = org.mockito.ArgumentCaptor.forClass(DocumentDraft.class);
        verify(drafts, org.mockito.Mockito.times(2)).save(content.capture());
        assertThat(content.getAllValues()).extracting(DocumentDraft::getLocale).containsExactlyInAnyOrder("en", "de");
        assertThat(content.getAllValues()).allSatisfy(draft -> assertThat(draft.getBlocks()).isEmpty());
        verifyNoInteractions(publications);
    }

    private ImportDocuments importer(DocumentAccessPolicy policy) {
        return new ImportDocuments(documents, drafts, publications, new DocumentReferentialIntegrityChecker(),
                TestGuards.with(policy), TestFacts.ignored());
    }

    private static DocumentCommand candidate(String slug) {
        return new DocumentCommand(new DocumentProperties(slug, "Replacement", null, null, null, "en", false,
                DocumentRoles.unrestricted(), DocumentPorts.empty()), List.of());
    }

    private static Document stored(String slug, String editor) {
        Document document = new Document("demo", slug, slug, "Original", "en", "ada");
        document.replaceProperties(slug, "Original", null, null, "ada", "en", false,
                new DocumentRoles(List.of(), List.of(editor), List.of()), DocumentPorts.empty());
        return document;
    }

    private static DocumentContent contentBoundToMissingPort() {
        return DocumentContent.of(List.of(new DocumentBlock("grid", BlockKind.WIDGET, null, null, null,
                "grid", null, Map.of("rows", "missing"), null)));
    }
}
