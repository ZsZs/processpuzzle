package com.processpuzzle.document.adapter.inbound;

import com.processpuzzle.document.domain.ContentBook;
import com.processpuzzle.document.domain.ContentBookNode;
import com.processpuzzle.document.domain.ContentBookNodes;
import com.processpuzzle.document.domain.DocumentRoles;
import com.processpuzzle.document.model.ContentBookInput;
import com.processpuzzle.document.model.ContentBookNodesInput;
import com.processpuzzle.document.usecase.ContentBookCommand;
import com.processpuzzle.document.usecase.CreateContentBook;
import com.processpuzzle.document.usecase.DeleteContentBook;
import com.processpuzzle.document.usecase.FindAllContentBooks;
import com.processpuzzle.document.usecase.FindContentBook;
import com.processpuzzle.document.usecase.ReplaceContentBookNodes;
import com.processpuzzle.document.usecase.UpdateContentBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Delegation, the UUID-to-string bridge, statuses, and the mapping both ways through {@link ContentBookMapper}. */
class ContentBookEndpointTest {

    private static final String ORG = "demo";
    private static final UUID BOOK_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID DOC_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DOC_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private FindAllContentBooks findAll;
    private FindContentBook find;
    private CreateContentBook create;
    private UpdateContentBook update;
    private DeleteContentBook delete;
    private ReplaceContentBookNodes replaceNodes;
    private ContentBookEndpoint endpoint;

    @BeforeEach
    void setUp() {
        findAll = mock(FindAllContentBooks.class);
        find = mock(FindContentBook.class);
        create = mock(CreateContentBook.class);
        update = mock(UpdateContentBook.class);
        delete = mock(DeleteContentBook.class);
        replaceNodes = mock(ReplaceContentBookNodes.class);
        endpoint = new ContentBookEndpoint(findAll, find, create, update, delete, replaceNodes, new ContentBookMapper());
    }

    @Test
    void listMapsThePage() {
        when(findAll.execute(ORG, "slug==handbook", null, 0, 20)).thenReturn(new PageImpl<>(List.of(book())));

        var response = endpoint.listContentBooks(ORG, "slug==handbook", null, 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTotalElements()).isEqualTo(1);
        assertThat(response.getBody().getContent()).singleElement().satisfies(model -> {
            assertThat(model.getId()).isEqualTo(BOOK_ID);
            assertThat(model.getOrgKey()).isEqualTo(ORG);
            assertThat(model.getIsPublic()).isTrue();
            assertThat(model.getReaderRoles()).containsExactly("reader");
            assertThat(model.getEditorRoles()).containsExactly("editor");
            assertThat(model.getNodes()).hasSize(2);
            assertThat(model.getNodes().get(1).getDocumentId()).isEqualTo(DOC_B);
            assertThat(model.getNodes().get(1).getParentId()).isEqualTo("a");
        });
    }

    @Test
    void createAnswers201AndMapsTheInput() {
        when(create.execute(eq(ORG), any())).thenReturn(book());

        var response = endpoint.createContentBook(ORG, input().nodes(List.of(
                new com.processpuzzle.document.model.ContentBookNode().id("a").documentId(DOC_A).orderIndex(null))));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ArgumentCaptor<ContentBookCommand> command = ArgumentCaptor.forClass(ContentBookCommand.class);
        verify(create).execute(eq(ORG), command.capture());
        assertThat(command.getValue().slug()).isEqualTo("handbook");
        assertThat(command.getValue().isPublic()).isTrue();
        assertThat(command.getValue().roles().editorRoles()).containsExactly("editor");
        assertThat(command.getValue().nodes()).containsExactly(new ContentBookNode("a", DOC_A.toString(), null, 0));
    }

    @Test
    void anInputWithoutNodesLeavesTheTreeAlone() {
        when(update.execute(eq(ORG), eq(BOOK_ID.toString()), any())).thenReturn(book());

        var response = endpoint.updateContentBook(ORG, BOOK_ID, input().isPublic(null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<ContentBookCommand> command = ArgumentCaptor.forClass(ContentBookCommand.class);
        verify(update).execute(eq(ORG), eq(BOOK_ID.toString()), command.capture());
        assertThat(command.getValue().nodes()).isNull();
        assertThat(command.getValue().isPublic()).isFalse();
    }

    @Test
    void getDelegatesWithTheStringId() {
        when(find.execute(ORG, BOOK_ID.toString())).thenReturn(book());

        assertThat(endpoint.getContentBook(ORG, BOOK_ID).getBody().getSlug()).isEqualTo("handbook");
    }

    @Test
    void deleteAnswers204() {
        assertThat(endpoint.deleteContentBook(ORG, BOOK_ID).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(delete).execute(ORG, BOOK_ID.toString());
    }

    @Test
    void replaceNodesMapsEveryNode() {
        when(replaceNodes.execute(eq(ORG), eq(BOOK_ID.toString()), anyList())).thenReturn(book());

        endpoint.replaceContentBookNodes(ORG, BOOK_ID, new ContentBookNodesInput().nodes(List.of(
                new com.processpuzzle.document.model.ContentBookNode().id("b").documentId(DOC_B).parentId("a").orderIndex(3))));

        verify(replaceNodes).execute(ORG, BOOK_ID.toString(), List.of(new ContentBookNode("b", DOC_B.toString(), "a", 3)));
    }

    private static ContentBookInput input() {
        return new ContentBookInput().slug("handbook").title("Handbook").isPublic(true)
                .readerRoles(List.of("reader")).editorRoles(List.of("editor"));
    }

    private static ContentBook book() {
        ContentBook book = new ContentBook(ORG, BOOK_ID.toString(), "handbook", "Handbook", "ada");
        book.replaceProperties("handbook", "Handbook", "All of it", true,
                new DocumentRoles(List.of("reader"), List.of("editor"), List.of()));
        book.replaceNodes(new ContentBookNodes(List.of(
                new ContentBookNode("a", DOC_A.toString(), null, 0), new ContentBookNode("b", DOC_B.toString(), "a", 0))));
        return book;
    }
}
