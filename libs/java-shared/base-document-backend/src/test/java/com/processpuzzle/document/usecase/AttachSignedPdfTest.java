package com.processpuzzle.document.usecase;

import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentSignature;
import com.processpuzzle.document.domain.DocumentSignatureRepository;
import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import com.processpuzzle.document.usecase.exception.DocumentFileStoreUnavailableException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentPublishingConflictException;
import com.processpuzzle.document.usecase.port.DocumentFileStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttachSignedPdfTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";
    private static final String SIGNATURE = "22222222-2222-2222-2222-222222222222";
    private static final byte[] PDF = "%PDF-1.7 content".getBytes(StandardCharsets.UTF_8);

    private DocumentRepository repository;
    private DocumentSignatureRepository signatureRepository;
    private CurrentPrincipal principal;
    private DocumentFileStore store;
    private DocumentSignature signature;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        signatureRepository = mock(DocumentSignatureRepository.class);
        principal = mock(CurrentPrincipal.class);
        store = mock(DocumentFileStore.class);
        signature = new DocumentSignature(SIGNATURE, ORG, ID, "Invoice", "en", 1L, "hash", "order", "o-1", "Ada",
                Instant.parse("2026-10-17T10:00:00Z"));
        when(repository.findByOrgKeyAndId(ORG, ID)).thenReturn(Optional.of(new Document(ORG, ID, "invoice", "Invoice", "en", "ada")));
        when(signatureRepository.findByOrgKeyAndIdAndDocumentId(ORG, SIGNATURE, ID)).thenReturn(Optional.of(signature));
        when(signatureRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(principal.isMemberOf(ORG)).thenReturn(true);
        when(principal.displayName()).thenReturn(Optional.of("Ada"));
        when(store.store(any(), any(), any())).thenReturn(new DocumentFileStore.StoredFile("demo-documents", "obj-1"));
    }

    @Test
    void storesThePdfAndRecordsWhereItIsAndItsHash() {
        DocumentSignature result = useCase(store).execute(ORG, ID, SIGNATURE, PDF, "invoice.pdf", "application/pdf");

        assertThat(result.hasPdf()).isTrue();
        assertThat(result.getPdfBucket()).isEqualTo("demo-documents");
        assertThat(result.getPdfObjectId()).isEqualTo("obj-1");
        assertThat(result.getPdfFileName()).isEqualTo("invoice.pdf");
        assertThat(result.getPdfSha256()).isEqualTo(AttachSignedPdf.sha256(PDF)).hasSize(64);
        verify(store).store(PDF, "invoice.pdf", "application/pdf");
    }

    @Test
    void onlyTheSignerAttaches() {
        when(principal.displayName()).thenReturn(Optional.of("Grace"));
        AttachSignedPdf useCase = useCase(store);

        assertThatThrownBy(() -> useCase.execute(ORG, ID, SIGNATURE, PDF, "a.pdf", "application/pdf"))
                .isInstanceOf(DocumentAccessDeniedException.class);
        verify(store, never()).store(any(), any(), any());
    }

    @Test
    void aSecondPdfIsRefused() {
        AttachSignedPdf useCase = useCase(store);
        useCase.execute(ORG, ID, SIGNATURE, PDF, "a.pdf", "application/pdf");

        assertThatThrownBy(() -> useCase.execute(ORG, ID, SIGNATURE, PDF, "b.pdf", "application/pdf"))
                .isInstanceOf(DocumentPublishingConflictException.class)
                .satisfies(error -> assertThat(((DocumentPublishingConflictException) error).getErrorId())
                        .isEqualTo("document.sign.pdf-already-attached"));
    }

    @Test
    void refusesWhatIsNotAPdf() {
        AttachSignedPdf useCase = useCase(store);
        byte[] gif = "GIF89a".getBytes(StandardCharsets.UTF_8);
        byte[] empty = new byte[0];

        assertThatThrownBy(() -> useCase.execute(ORG, ID, SIGNATURE, PDF, "a.png", "image/png"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(ORG, ID, SIGNATURE, gif, "a.pdf", "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(ORG, ID, SIGNATURE, empty, "a.pdf", "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(store, never()).store(any(), any(), any());
    }

    @Test
    void refusesAPdfOverTheLimit() {
        byte[] big = new byte[AttachSignedPdf.MAX_BYTES + 1];
        System.arraycopy(PDF, 0, big, 0, 4);
        AttachSignedPdf useCase = useCase(store);

        assertThatThrownBy(() -> useCase.execute(ORG, ID, SIGNATURE, big, "a.pdf", "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withoutAFileStoreItIsUnavailable() {
        AttachSignedPdf useCase = useCase(null);

        assertThatThrownBy(() -> useCase.execute(ORG, ID, SIGNATURE, PDF, "a.pdf", "application/pdf"))
                .isInstanceOf(DocumentFileStoreUnavailableException.class);
        assertThat(signature.hasPdf()).isFalse();
    }

    @Test
    void aSignatureOfAnotherDocumentIsNotFound() {
        when(signatureRepository.findByOrgKeyAndIdAndDocumentId(ORG, SIGNATURE, ID)).thenReturn(Optional.empty());
        AttachSignedPdf useCase = useCase(store);

        assertThatThrownBy(() -> useCase.execute(ORG, ID, SIGNATURE, PDF, "a.pdf", "application/pdf"))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    private AttachSignedPdf useCase(DocumentFileStore fileStore) {
        return new AttachSignedPdf(repository, signatureRepository, TestGuards.permitAll(), provider(principal),
                provider(fileStore));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        when(provider.getIfUnique()).thenReturn(value);
        return provider;
    }
}
