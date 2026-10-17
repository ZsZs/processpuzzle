package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentSignature;
import com.processpuzzle.document.domain.DocumentSignatureRepository;
import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindDocumentSignaturesTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private DocumentRepository repository;
    private DocumentSignatureRepository signatureRepository;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        signatureRepository = mock(DocumentSignatureRepository.class);
        when(repository.findByOrgKeyAndId(ORG, ID))
                .thenReturn(Optional.of(new Document(ORG, ID, "contract", "Contract", "en", "ada")));
        when(signatureRepository.findByOrgKeyAndDocumentIdOrderBySignedAtDesc(ORG, ID)).thenReturn(List.of(
                signature("s-1", "employee", "e-1"), signature("s-2", "employee", "e-2"),
                signature("s-3", "supplier", "e-1")));
    }

    @Test
    void withoutAFilterListsEverySignature() {
        assertThat(useCase(TestGuards.permitAll()).execute(ORG, ID, null, null))
                .extracting(DocumentSignature::getId).containsExactly("s-1", "s-2", "s-3");
    }

    @Test
    void narrowsToTheSubjectTypeAndId() {
        FindDocumentSignatures useCase = useCase(TestGuards.permitAll());

        assertThat(useCase.execute(ORG, ID, "employee", null)).extracting(DocumentSignature::getId)
                .containsExactly("s-1", "s-2");
        assertThat(useCase.execute(ORG, ID, null, "e-1")).extracting(DocumentSignature::getId)
                .containsExactly("s-1", "s-3");
        assertThat(useCase.execute(ORG, ID, "employee", "e-1")).extracting(DocumentSignature::getId)
                .containsExactly("s-1");
    }

    @Test
    void aReaderWhoMayNotSeeTheDocumentGetsNotFound() {
        DocumentAccessPolicy policy = mock(DocumentAccessPolicy.class);
        when(policy.isAuthenticated()).thenReturn(true);
        doThrow(new DocumentAccessDeniedException("Not a member")).when(policy).requireAccess(ORG);

        assertThatThrownBy(() -> useCase(TestGuards.with(policy)).execute(ORG, ID, null, null))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void anUnknownDocumentIsNotFound() {
        when(repository.findByOrgKeyAndId(ORG, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase(TestGuards.permitAll()).execute(ORG, "missing", null, null))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    private FindDocumentSignatures useCase(com.processpuzzle.document.usecase.service.DocumentGuard guard) {
        return new FindDocumentSignatures(repository, signatureRepository, guard);
    }

    private static DocumentSignature signature(String id, String subjectType, String subjectId) {
        return new DocumentSignature(id, ORG, ID, "Contract", "en", 1L, "hash", subjectType, subjectId, "ada",
                Instant.parse("2026-10-17T10:00:00Z"));
    }
}
