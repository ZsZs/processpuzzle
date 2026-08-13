package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentRoles;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FindAllDocumentsTest {

    private static final String ORG = "demo";
    private static final String PUBLIC_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PRIVATE_ID = "22222222-2222-2222-2222-222222222222";

    private DocumentRepository repository;
    private DocumentDraftRepository draftRepository;
    private PublishedDocumentRepository publishedRepository;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        draftRepository = mock(DocumentDraftRepository.class);
        publishedRepository = mock(PublishedDocumentRepository.class);
    }

    @Test
    void defaultsToTheFirstPageOfTwentyWhenTheRequestSaysNothing() {
        stubPage(List.of());

        findAll(TestPolicies.permitAll()).execute(ORG, null, null, null, null);

        Pageable pageable = capturedPageable();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().isSorted()).isFalse();
    }

    @Test
    void honoursAnExplicitPageSizeAndSortOrder() {
        stubPage(List.of());

        findAll(TestPolicies.permitAll()).execute(ORG, null, "title,desc", 2, 5);

        Pageable pageable = capturedPageable();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort().getOrderFor("title").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void anRsqlFilterIsAndedOntoTheTenantSpecificationRatherThanReplacingIt() {
        // RSQL permits a top-level OR, which would otherwise escape the tenant filter entirely.
        stubPage(List.of(document(PUBLIC_ID, true, DocumentRoles.unrestricted())));

        FindAllDocuments.Result result = findAll(TestPolicies.permitAll()).execute(ORG, "title==Getting*", null, null, null);

        assertThat(capturedSpecification()).isNotNull();
        assertThat(result.page().getContent()).hasSize(1);
    }

    @Test
    void theTenantPredicateIsWhatTheSpecificationActuallyBuilds() {
        stubPage(List.of());
        findAll(TestPolicies.permitAll()).execute(ORG, null, null, null, null);

        Root<Document> root = mock(Root.class);
        Path<Object> orgKey = mock(Path.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate tenantPredicate = mock(Predicate.class);
        when(root.get("orgKey")).thenReturn(orgKey);
        when(cb.equal(orgKey, ORG)).thenReturn(tenantPredicate);

        assertThat(capturedSpecification().toPredicate(root, null, cb)).isSameAs(tenantPredicate);
    }

    @Test
    void documentsTheReaderMayNotSeeAreDroppedFromThePage() {
        // Filtering after the query is why a page can come back shorter than the requested size.
        stubPage(List.of(
                document(PUBLIC_ID, true, DocumentRoles.unrestricted()),
                document(PRIVATE_ID, false, new DocumentRoles(List.of("insider"), List.of(), List.of()))));

        FindAllDocuments.Result result = findAll(TestPolicies.holding("outsider")).execute(ORG, null, null, null, null);

        assertThat(result.page().getContent()).extracting(Document::getId).containsExactly(PUBLIC_ID);
        assertThat(result.statesByDocumentId()).containsOnlyKeys(PUBLIC_ID);
    }

    @Test
    void eachReadableDocumentComesBackWithItsPerLocaleState() {
        stubPage(List.of(document(PUBLIC_ID, true, DocumentRoles.unrestricted())));
        when(draftRepository.findByOrgKeyAndDocumentId(ORG, PUBLIC_ID)).thenReturn(List.of(
                new DocumentDraft(ORG, PUBLIC_ID, "en", DocumentContent.empty(), null),
                new DocumentDraft(ORG, PUBLIC_ID, "de", DocumentContent.empty(), 1L)));

        FindAllDocuments.Result result = findAll(TestPolicies.permitAll()).execute(ORG, null, null, null, null);

        assertThat(result.statesByDocumentId().get(PUBLIC_ID))
                .extracting(DocumentTranslationView::locale).containsExactly("en", "de");
    }

    @Test
    void listingRequiresMembershipOfTheOrganization() {
        assertThatThrownBy(() -> findAll(TestPolicies.outsider()).execute(ORG, null, null, null, null))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    // region fixtures
    private FindAllDocuments findAll(DocumentAccessPolicy policy) {
        return new FindAllDocuments(repository,
                new DocumentTranslationAssembler(draftRepository, publishedRepository),
                TestGuards.with(policy));
    }

    private void stubPage(List<Document> documents) {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(
                invocation -> new PageImpl<>(documents, invocation.getArgument(1), documents.size()));
    }

    private Pageable capturedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), captor.capture());
        return captor.getValue();
    }

    private Specification<Document> capturedSpecification() {
        ArgumentCaptor<Specification<Document>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(captor.capture(), any(Pageable.class));
        return captor.getValue();
    }

    private static Document document(String id, boolean isPublic, DocumentRoles roles) {
        Document document = new Document(ORG, id, "doc-" + id, "Doc", "en", "ada");
        document.replaceProperties("doc-" + id, "Doc", null, null, "ada", "en", isPublic, roles, DocumentPorts.empty());
        return document;
    }
    // endregion
}
