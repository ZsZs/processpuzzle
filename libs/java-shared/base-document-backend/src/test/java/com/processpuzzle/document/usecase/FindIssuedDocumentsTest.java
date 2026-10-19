package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentSignature;
import com.processpuzzle.document.domain.DocumentSignatureRepository;
import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FindIssuedDocumentsTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private DocumentRepository documents;
    private DocumentSignatureRepository signatures;

    @BeforeEach
    void setUp() {
        documents = mock(DocumentRepository.class);
        signatures = mock(DocumentSignatureRepository.class);
        when(documents.findByOrgKeyAndId(ORG, ID)).thenReturn(Optional.of(new Document(ORG, ID, "invoice", "Invoice", "en", "ada")));
    }

    @Test
    void passesTheFiltersAndPagingToTheRepository() {
        when(signatures.findIssued(eq(ORG), eq(ID), eq("order"), eq("o-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(signature()), PageRequest.of(1, 5), 6));

        var page = new FindIssuedDocuments(signatures, documents, TestGuards.permitAll())
                .execute(ORG, ID, "order", "o-1", 1, 5);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(6);
        verify(signatures).findIssued(ORG, ID, "order", "o-1", PageRequest.of(1, 5));
    }

    @Test
    void leavesOutSignaturesOfDocumentsTheCallerMayNotRead() {
        when(signatures.findIssued(eq(ORG), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(signature())));
        DocumentAccessPolicy policy = mock(DocumentAccessPolicy.class);
        when(policy.isAuthenticated()).thenReturn(true);
        when(policy.hasAnyRole(any())).thenReturn(false);
        Document restricted = new Document(ORG, ID, "invoice", "Invoice", "en", "ada");
        restricted.replaceProperties("invoice", "Invoice", null, null, "ada", "en", false,
                new com.processpuzzle.document.domain.DocumentRoles(List.of("auditor"), List.of(), List.of()),
                restricted.getPorts());
        when(documents.findByOrgKeyAndId(ORG, ID)).thenReturn(Optional.of(restricted));

        var page = new FindIssuedDocuments(signatures, documents, TestGuards.with(policy))
                .execute(ORG, null, null, null, null, null);

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void requiresOrganizationAccess() {
        DocumentAccessPolicy policy = mock(DocumentAccessPolicy.class);
        doThrow(new DocumentAccessDeniedException("no")).when(policy).requireAccess(ORG);
        FindIssuedDocuments useCase = new FindIssuedDocuments(signatures, documents, TestGuards.with(policy));

        assertThatThrownBy(() -> useCase.execute(ORG, null, null, null, null, null))
                .isInstanceOf(DocumentAccessDeniedException.class);
    }

    private static DocumentSignature signature() {
        return new DocumentSignature("s-1", ORG, ID, "Invoice", "en", 1L, "hash", "order", "o-1", "Ada",
                Instant.parse("2026-10-17T10:00:00Z"));
    }
}
