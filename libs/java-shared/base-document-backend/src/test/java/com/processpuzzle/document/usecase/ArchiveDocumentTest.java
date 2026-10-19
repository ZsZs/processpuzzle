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
import com.processpuzzle.document.usecase.exception.DocumentFileStoreUnavailableException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentPublishingConflictException;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import com.processpuzzle.document.usecase.port.DocumentFileStore;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchiveDocumentTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private DocumentRepository repository;
    private DocumentDraftRepository draftRepository;
    private PublishedDocumentRepository publishedRepository;
    private DocumentFileStore store;
    private Document document;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        draftRepository = mock(DocumentDraftRepository.class);
        publishedRepository = mock(PublishedDocumentRepository.class);
        store = mock(DocumentFileStore.class);
        document = new Document(ORG, ID, "handbook", "Handbook", "en", "ada");
        when(repository.findByOrgKeyAndId(ORG, ID)).thenReturn(Optional.of(document));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DocumentDraft draft = new DocumentDraft(ORG, ID, "en", DocumentContent.of(List.of(text("draft-block"))), null);
        when(draftRepository.findByOrgKeyAndDocumentId(ORG, ID)).thenReturn(List.of(draft));
        when(publishedRepository.findByOrgKeyAndDocumentId(ORG, ID)).thenReturn(List.of(new PublishedDocument(ORG, ID, "en",
                DocumentContent.of(List.of(text("published-block"))), 1L, Instant.parse("2026-10-18T10:00:00Z"), "ada")));
        when(store.store(any(), any(), any())).thenReturn(new DocumentFileStore.StoredFile("demo-documents", "obj-1"));
    }

    @Test
    void storesASnapshotOfDraftAndPublishedContentAndLocksTheDocument() {
        DocumentDetails details = useCase(store, TestPolicies.permitAll()).execute(ORG, ID);

        ArgumentCaptor<byte[]> content = ArgumentCaptor.forClass(byte[].class);
        verify(store).store(content.capture(), eq("handbook-archive.json"), eq(ArchiveDocument.JSON));
        assertThat(new String(content.getValue(), StandardCharsets.UTF_8))
                .contains("\"slug\" : \"handbook\"", "draft-block", "published-block", "\"publishedRevision\" : 1");
        assertThat(details.document().isArchived()).isTrue();
        assertThat(details.document().getArchiveBucket()).isEqualTo("demo-documents");
        assertThat(details.document().getArchiveObjectId()).isEqualTo("obj-1");
        assertThat(details.document().getArchiveName()).isEqualTo("handbook-archive.json");
    }

    @Test
    void aDocumentIsArchivedOnce() {
        document.archive(Instant.now(), "ada", "demo-documents", "obj-0", "handbook-archive.json");
        ArchiveDocument useCase = useCase(store, TestPolicies.permitAll());

        assertThatThrownBy(() -> useCase.execute(ORG, ID)).isInstanceOf(DocumentPublishingConflictException.class);
        verify(store, never()).store(any(), any(), any());
    }

    @Test
    void withoutAFileStoreNothingIsArchived() {
        ArchiveDocument useCase = useCase(null, TestPolicies.permitAll());

        assertThatThrownBy(() -> useCase.execute(ORG, ID)).isInstanceOf(DocumentFileStoreUnavailableException.class);
        assertThat(document.isArchived()).isFalse();
    }

    @Test
    void requiresAPublisher() {
        ArchiveDocument useCase = useCase(store, TestPolicies.holding("reader"));
        document.replaceProperties("handbook", "Handbook", null, null, "ada", "en", false,
                new DocumentRoles(List.of(), List.of("editor"), List.of("publisher")),
                DocumentPorts.empty());

        assertThatThrownBy(() -> useCase.execute(ORG, ID)).isInstanceOf(DocumentAccessDeniedException.class);
    }

    @Test
    void unknownDocumentIsNotFound() {
        when(repository.findByOrgKeyAndId(ORG, "missing")).thenReturn(Optional.empty());
        ArchiveDocument useCase = useCase(store, TestPolicies.permitAll());

        assertThatThrownBy(() -> useCase.execute(ORG, "missing")).isInstanceOf(DocumentNotFoundException.class);
    }

    private ArchiveDocument useCase(DocumentFileStore fileStore, DocumentAccessPolicy policy) {
        return new ArchiveDocument(repository, draftRepository, publishedRepository,
                new DocumentTranslationAssembler(draftRepository, publishedRepository), TestGuards.with(policy), provider(fileStore));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique()).thenReturn(value);
        return provider;
    }

    private static DocumentBlock text(String id) {
        return new DocumentBlock(id, BlockKind.TEXT, true, null, null, null, null, null, null);
    }
}
