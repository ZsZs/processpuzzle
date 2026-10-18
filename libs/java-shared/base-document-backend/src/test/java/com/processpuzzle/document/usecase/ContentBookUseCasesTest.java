package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.ContentBook;
import com.processpuzzle.document.domain.ContentBookKey;
import com.processpuzzle.document.domain.ContentBookNode;
import com.processpuzzle.document.domain.ContentBookRepository;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentKind;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentRoles;
import com.processpuzzle.document.usecase.exception.ContentBookInvalidTreeException;
import com.processpuzzle.document.usecase.exception.ContentBookNotFoundException;
import com.processpuzzle.document.usecase.exception.ContentBookSlugAlreadyExistsException;
import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import com.processpuzzle.document.usecase.service.ContentBookTreeChecker;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The six content book use cases: the guard rung each one takes, and what reaches the repository. */
class ContentBookUseCasesTest {

    private static final String ORG = "demo";
    private static final String BOOK_ID = "book-1";
    private static final String DOC_A = "11111111-1111-1111-1111-111111111111";
    private static final String DOC_B = "22222222-2222-2222-2222-222222222222";
    private static final DocumentRoles EDITORS_ONLY = new DocumentRoles(List.of("reader"), List.of("editor"), List.of());

    private ContentBookRepository repository;
    private DocumentRepository documents;

    @BeforeEach
    void setUp() {
        repository = mock(ContentBookRepository.class);
        documents = mock(DocumentRepository.class);
        when(repository.save(any(ContentBook.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(documents.findAllById(anyIterable())).thenReturn(List.of(document(DOC_A, DocumentKind.DOCUMENT),
                document(DOC_B, DocumentKind.DOCUMENT)));
    }

    // region find
    @Test
    void findReturnsAReadableBook() {
        givenBook(book(false, EDITORS_ONLY));

        assertThat(new FindContentBook(repository, guard(TestPolicies.holding("reader"))).execute(ORG, BOOK_ID))
                .extracting(ContentBook::getSlug).isEqualTo("handbook");
    }

    @Test
    void aBookThePrincipalMayNotReadIsNotFound() {
        givenBook(book(false, EDITORS_ONLY));

        assertThatThrownBy(() -> new FindContentBook(repository, guard(TestPolicies.holding("other"))).execute(ORG, BOOK_ID))
                .isInstanceOf(ContentBookNotFoundException.class);
    }

    @Test
    void aPublicBookIsReadableAnonymously() {
        givenBook(book(true, EDITORS_ONLY));

        assertThat(new FindContentBook(repository, guard(TestPolicies.anonymous())).execute(ORG, BOOK_ID)).isNotNull();
    }

    @Test
    void anUnknownBookIsNotFound() {
        when(repository.findByOrgKeyAndId(ORG, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new FindContentBook(repository, guard(TestPolicies.permitAll())).execute(ORG, "missing"))
                .isInstanceOf(ContentBookNotFoundException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllDropsTheBooksThePrincipalMayNotRead() {
        ContentBook readable = book(false, DocumentRoles.unrestricted());
        ContentBook restricted = new ContentBook(ORG, "book-2", "secret", "Secret", "ada");
        restricted.replaceProperties("secret", "Secret", null, false,
                new DocumentRoles(List.of("secret-reader"), List.of(), List.of()));
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(readable, restricted)));

        Page<ContentBook> page = new FindAllContentBooks(repository, guard(TestPolicies.holding("reader")))
                .execute(ORG, "title==Handbook", "title,asc", 0, 10);

        assertThat(page.getContent()).containsExactly(readable);
    }
    // endregion

    // region create
    @Test
    void createMintsAnIdAndRecordsTheCreator() {
        ContentBook created = create(TestPolicies.principal("ada"), command("handbook", null));

        assertThat(created.getId()).isNotBlank();
        assertThat(created.getOrgKey()).isEqualTo(ORG);
        assertThat(created.getCreatedBy()).isEqualTo("ada");
        assertThat(created.getNodes().nodes()).isEmpty();
    }

    @Test
    void createStoresACheckedTree() {
        ContentBook created = create(TestPolicies.permitAll(), command("handbook", List.of(
                new ContentBookNode("a", DOC_A, null, 5), new ContentBookNode("b", DOC_B, "a", 9))));

        assertThat(created.getNodes().nodes()).containsExactly(
                new ContentBookNode("a", DOC_A, null, 0), new ContentBookNode("b", DOC_B, "a", 0));
    }

    @Test
    void createRefusesATakenSlug() {
        when(repository.existsByOrgKeyAndSlug(ORG, "handbook")).thenReturn(true);

        assertThatThrownBy(() -> create(TestPolicies.permitAll(), command("handbook", null)))
                .isInstanceOf(ContentBookSlugAlreadyExistsException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void createRequiresMembership() {
        assertThatThrownBy(() -> create(TestPolicies.outsider(), command("handbook", null)))
                .isInstanceOf(IllegalStateException.class);
    }
    // endregion

    // region update
    @Test
    void updateReplacesThePropertiesAndKeepsTheTreeWhenNoneIsSent() {
        ContentBook existing = book(false, EDITORS_ONLY);
        existing.replaceNodes(new ContentBookTreeChecker(documents).check(ORG, List.of(new ContentBookNode("a", DOC_A, null, 0))));
        givenBook(existing);

        ContentBook updated = new UpdateContentBook(repository, new ContentBookTreeChecker(documents),
                guard(TestPolicies.holding("editor"))).execute(ORG, BOOK_ID,
                new ContentBookCommand("handbook", "Renamed", "New", true, EDITORS_ONLY, null));

        assertThat(updated.getTitle()).isEqualTo("Renamed");
        assertThat(updated.isPublic()).isTrue();
        assertThat(updated.getNodes().documentIds()).containsExactly(DOC_A);
    }

    @Test
    void updateReplacesTheTreeWhenOneIsSent() {
        givenBook(book(false, EDITORS_ONLY));

        ContentBook updated = new UpdateContentBook(repository, new ContentBookTreeChecker(documents),
                guard(TestPolicies.holding("editor"))).execute(ORG, BOOK_ID,
                command("handbook", List.of(new ContentBookNode("b", DOC_B, null, 0))));

        assertThat(updated.getNodes().documentIds()).containsExactly(DOC_B);
    }

    @Test
    void updateRefusesASlugAnotherBookHolds() {
        givenBook(book(false, EDITORS_ONLY));
        when(repository.existsByOrgKeyAndSlug(ORG, "taken")).thenReturn(true);

        assertThatThrownBy(() -> new UpdateContentBook(repository, new ContentBookTreeChecker(documents),
                guard(TestPolicies.permitAll())).execute(ORG, BOOK_ID, command("taken", null)))
                .isInstanceOf(ContentBookSlugAlreadyExistsException.class);
    }

    @Test
    void updateRequiresAnEditorOfTheBook() {
        givenBook(book(false, EDITORS_ONLY));

        assertThatThrownBy(() -> new UpdateContentBook(repository, new ContentBookTreeChecker(documents),
                guard(TestPolicies.holding("reader"))).execute(ORG, BOOK_ID, command("handbook", null)))
                .isInstanceOf(DocumentAccessDeniedException.class)
                .hasMessageContaining("content book 'book-1'");
    }

    @Test
    void updateOfAnUnknownBookIsNotFound() {
        when(repository.findByOrgKeyAndId(ORG, BOOK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new UpdateContentBook(repository, new ContentBookTreeChecker(documents),
                guard(TestPolicies.permitAll())).execute(ORG, BOOK_ID, command("handbook", null)))
                .isInstanceOf(ContentBookNotFoundException.class);
    }
    // endregion

    // region replace nodes
    @Test
    void replaceNodesStoresTheWholeTree() {
        givenBook(book(false, EDITORS_ONLY));

        ContentBook updated = replaceNodes(TestPolicies.holding("editor"),
                List.of(new ContentBookNode("b", DOC_B, null, 0), new ContentBookNode("a", DOC_A, "b", 0)));

        assertThat(updated.getNodes().documentIds()).containsExactly(DOC_B, DOC_A);
        verify(repository).save(updated);
    }

    @Test
    void replaceNodesRefusesAnInvalidTree() {
        givenBook(book(false, EDITORS_ONLY));

        assertThatThrownBy(() -> replaceNodes(TestPolicies.permitAll(),
                List.of(new ContentBookNode("a", DOC_A, "a", 0))))
                .isInstanceOf(ContentBookInvalidTreeException.class);
    }

    @Test
    void replaceNodesRefusesAnUnknownDocument() {
        givenBook(book(false, EDITORS_ONLY));
        when(documents.findAllById(anyIterable())).thenReturn(List.of(document(DOC_A, DocumentKind.DOCUMENT)));

        assertThatThrownBy(() -> replaceNodes(TestPolicies.permitAll(),
                List.of(new ContentBookNode("a", DOC_A, null, 0), new ContentBookNode("b", DOC_B, null, 1))))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining(DOC_B);
    }

    @Test
    void replaceNodesRefusesAReportTemplate() {
        givenBook(book(false, EDITORS_ONLY));
        when(documents.findAllById(anyIterable())).thenReturn(List.of(document(DOC_A, DocumentKind.REPORT_TEMPLATE)));

        assertThatThrownBy(() -> replaceNodes(TestPolicies.permitAll(), List.of(new ContentBookNode("a", DOC_A, null, 0))))
                .isInstanceOf(ContentBookInvalidTreeException.class)
                .hasMessageContaining("REPORT_TEMPLATE");
    }

    @Test
    void replaceNodesRequiresAnEditorOfTheBook() {
        givenBook(book(false, EDITORS_ONLY));

        assertThatThrownBy(() -> replaceNodes(TestPolicies.holding("reader"), List.of()))
                .isInstanceOf(DocumentAccessDeniedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void replaceNodesOfAnUnknownBookIsNotFound() {
        when(repository.findByOrgKeyAndId(ORG, BOOK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> replaceNodes(TestPolicies.permitAll(), List.of()))
                .isInstanceOf(ContentBookNotFoundException.class);
    }
    // endregion

    // region delete
    @Test
    void deleteRemovesTheBook() {
        givenBook(book(false, EDITORS_ONLY));

        new DeleteContentBook(repository, guard(TestPolicies.holding("editor"))).execute(ORG, BOOK_ID);

        verify(repository).deleteById(new ContentBookKey(ORG, BOOK_ID));
    }

    @Test
    void deleteRequiresAnEditorOfTheBook() {
        givenBook(book(false, EDITORS_ONLY));

        assertThatThrownBy(() -> new DeleteContentBook(repository, guard(TestPolicies.holding("reader"))).execute(ORG, BOOK_ID))
                .isInstanceOf(DocumentAccessDeniedException.class);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void deleteOfAnUnknownBookIsNotFound() {
        when(repository.findByOrgKeyAndId(ORG, BOOK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DeleteContentBook(repository, guard(TestPolicies.permitAll())).execute(ORG, BOOK_ID))
                .isInstanceOf(ContentBookNotFoundException.class);
    }
    // endregion

    // region fixtures
    private ContentBook create(DocumentAccessPolicy policy, ContentBookCommand command) {
        return new CreateContentBook(repository, new ContentBookTreeChecker(documents), guard(policy)).execute(ORG, command);
    }

    private ContentBook replaceNodes(DocumentAccessPolicy policy, List<ContentBookNode> nodes) {
        return new ReplaceContentBookNodes(repository, new ContentBookTreeChecker(documents), guard(policy))
                .execute(ORG, BOOK_ID, nodes);
    }

    private void givenBook(ContentBook book) {
        when(repository.findByOrgKeyAndId(ORG, BOOK_ID)).thenReturn(Optional.of(book));
    }

    private static DocumentGuard guard(DocumentAccessPolicy policy) {
        return TestGuards.with(policy);
    }

    private static ContentBookCommand command(String slug, List<ContentBookNode> nodes) {
        return new ContentBookCommand(slug, "Handbook", null, false, null, nodes);
    }

    private static ContentBook book(boolean isPublic, DocumentRoles roles) {
        ContentBook book = new ContentBook(ORG, BOOK_ID, "handbook", "Handbook", "ada");
        book.replaceProperties("handbook", "Handbook", null, isPublic, roles);
        return book;
    }

    private static Document document(String id, DocumentKind kind) {
        Document document = new Document(ORG, id, "doc-" + id.charAt(0), "Doc", "en", "ada");
        document.changeKind(kind);
        return document;
    }
    // endregion
}
