package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentRoles;
import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExportDocumentQueryTest {
    @Test
    void editorialContentIsLoadedOnlyAfterEditorAuthorization() {
        var documents = mock(DocumentRepository.class);
        var drafts = mock(DocumentDraftRepository.class);
        var document = new Document("demo", "id", "slug", "Title", "en", "ada");
        document.replaceProperties("slug", "Title", null, null, "ada", "en", false,
                new DocumentRoles(List.of(), List.of("editor"), List.of()), null);
        when(documents.findByOrgKeyAndId("demo", "id")).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> new ExportDocument(documents, drafts, TestGuards.with(TestPolicies.holding("reader")))
                .execute("demo", "id")).isInstanceOf(DocumentAccessDeniedException.class);
        verifyNoInteractions(drafts);

        var draft = new DocumentDraft("demo", "id", "de", DocumentContent.empty(), 1L);
        when(drafts.findByOrgKeyAndDocumentId("demo", "id")).thenReturn(List.of(draft));
        var exported = new ExportDocument(documents, drafts, TestGuards.with(TestPolicies.holding("editor")))
                .execute("demo", "id");
        assertThat(exported.document()).isSameAs(document);
        assertThat(exported.drafts()).containsExactly(draft);
    }
}
