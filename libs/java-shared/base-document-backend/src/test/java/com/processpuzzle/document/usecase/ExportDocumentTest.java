package com.processpuzzle.document.usecase;

import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.domain.BlockKind;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentRoles;
import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExportDocumentTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private DocumentRepository repository;
    private DocumentDraftRepository draftRepository;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        draftRepository = mock(DocumentDraftRepository.class);
    }

    @Test
    void writesTheDocumentAndEveryTranslationsDraftAsAnImportableFile() {
        stubFound(DocumentRoles.unrestricted(),
                draft("en", "intro"),
                draft("de", "einleitung"));

        String yaml = export(TestPolicies.permitAll()).execute(ORG, ID);

        assertThat(yaml).startsWith("---");
        assertThat(yaml).contains("documents:");
        assertThat(yaml).contains("getting-started");
        assertThat(yaml).contains("intro").contains("einleitung");
        assertThat(yaml).contains("locale: \"en\"").contains("locale: \"de\"");
    }

    @Test
    void publicationStateIsDeliberatelyAbsentFromTheFile() {
        // An export is a copy of the source of truth; publishing is an editorial act in the target
        // organization, not a property of a file someone sent.
        stubFound(DocumentRoles.unrestricted(), draft("en", "intro"));

        String yaml = export(TestPolicies.permitAll()).execute(ORG, ID);

        assertThat(yaml).doesNotContain("publishedAt").doesNotContain("publishedRevision").doesNotContain("status");
    }

    @Test
    void exportingRequiresAnEditorRatherThanMerelyAReader() {
        // The file carries unpublished content and the role lists, so a reader must not reach it.
        stubFound(new DocumentRoles(List.of(), List.of("editor"), List.of()), draft("en", "intro"));

        assertThatThrownBy(() -> export(TestPolicies.holding("reader")).execute(ORG, ID))
                .isInstanceOf(DocumentAccessDeniedException.class);
        assertThat(export(TestPolicies.holding("editor")).execute(ORG, ID)).contains("getting-started");
    }

    @Test
    void unknownDocumentIsNotFound() {
        when(repository.findByOrgKeyAndId(ORG, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> export(TestPolicies.permitAll()).execute(ORG, "missing"))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void contentThatCannotBeSerializedNamesTheDocumentRatherThanLeakingAJacksonMessage() {
        // Widget props are the widget type's own business and are never inspected, so an unwritable
        // value can only be found out here.
        DocumentBlock unwritable = new DocumentBlock("intro", BlockKind.TEXT, true, null, null, null,
                Map.of("bad", new Unserializable()), Map.of(), Map.of());
        stubFound(DocumentRoles.unrestricted(),
                new DocumentDraft(ORG, ID, "en", DocumentContent.of(List.of(unwritable)), null));

        assertThatThrownBy(() -> export(TestPolicies.permitAll()).execute(ORG, ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot serialize document '" + ID + "'");
    }

    /** A prop value whose only property throws, so Jackson cannot write it. */
    static class Unserializable {
        public String getBoom() {
            throw new UnsupportedOperationException("not serializable");
        }
    }

    // region fixtures
    private ExportDocument export(DocumentAccessPolicy policy) {
        return new ExportDocument(repository, draftRepository, TestGuards.with(policy), new DocumentMapper());
    }

    private void stubFound(DocumentRoles roles, DocumentDraft... drafts) {
        Document document = new Document(ORG, ID, "getting-started", "Getting started", "en", "ada");
        document.replaceProperties("getting-started", "Getting started", null, null, "ada", "en", false, roles,
                DocumentPorts.empty());
        when(repository.findByOrgKeyAndId(ORG, ID)).thenReturn(Optional.of(document));
        when(draftRepository.findByOrgKeyAndDocumentId(ORG, ID)).thenReturn(List.of(drafts));
    }

    private static DocumentDraft draft(String locale, String blockId) {
        return new DocumentDraft(ORG, ID, locale, DocumentContent.of(List.of(
                new DocumentBlock(blockId, BlockKind.TEXT, true, null, null, null, null, null, null))), null);
    }
    // endregion
}
