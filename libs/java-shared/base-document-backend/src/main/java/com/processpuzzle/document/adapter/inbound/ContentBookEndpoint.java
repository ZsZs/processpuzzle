package com.processpuzzle.document.adapter.inbound;

import com.processpuzzle.core.logging.LogClass;
import com.processpuzzle.document.api.ContentBookApi;
import com.processpuzzle.document.model.ContentBook;
import com.processpuzzle.document.model.ContentBookInput;
import com.processpuzzle.document.model.ContentBookNodesInput;
import com.processpuzzle.document.model.PageOfContentBook;
import com.processpuzzle.document.usecase.CreateContentBook;
import com.processpuzzle.document.usecase.DeleteContentBook;
import com.processpuzzle.document.usecase.FindAllContentBooks;
import com.processpuzzle.document.usecase.FindContentBook;
import com.processpuzzle.document.usecase.ReplaceContentBookNodes;
import com.processpuzzle.document.usecase.UpdateContentBook;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** A thin delegator over the content book use cases; paths and verbs live in base-document-api.yaml. */
@RestController
@LogClass
public class ContentBookEndpoint implements ContentBookApi {

    private final FindAllContentBooks findAllContentBooks;
    private final FindContentBook findContentBook;
    private final CreateContentBook createContentBook;
    private final UpdateContentBook updateContentBook;
    private final DeleteContentBook deleteContentBook;
    private final ReplaceContentBookNodes replaceContentBookNodes;
    private final ContentBookMapper mapper;

    public ContentBookEndpoint(FindAllContentBooks findAllContentBooks,
                               FindContentBook findContentBook,
                               CreateContentBook createContentBook,
                               UpdateContentBook updateContentBook,
                               DeleteContentBook deleteContentBook,
                               ReplaceContentBookNodes replaceContentBookNodes,
                               ContentBookMapper mapper) {
        this.findAllContentBooks = findAllContentBooks;
        this.findContentBook = findContentBook;
        this.createContentBook = createContentBook;
        this.updateContentBook = updateContentBook;
        this.deleteContentBook = deleteContentBook;
        this.replaceContentBookNodes = replaceContentBookNodes;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<PageOfContentBook> listContentBooks(String orgKey, String where, String order, Integer page,
                                                              Integer size) {
        return ResponseEntity.ok(mapper.toModel(findAllContentBooks.execute(orgKey, where, order, page, size)));
    }

    @Override
    public ResponseEntity<ContentBook> createContentBook(String orgKey, ContentBookInput contentBookInput) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toModel(createContentBook.execute(orgKey, mapper.toCommand(contentBookInput))));
    }

    @Override
    public ResponseEntity<ContentBook> getContentBook(String orgKey, UUID contentBookId) {
        return ResponseEntity.ok(mapper.toModel(findContentBook.execute(orgKey, contentBookId.toString())));
    }

    @Override
    public ResponseEntity<ContentBook> updateContentBook(String orgKey, UUID contentBookId,
                                                         ContentBookInput contentBookInput) {
        return ResponseEntity.ok(mapper.toModel(
                updateContentBook.execute(orgKey, contentBookId.toString(), mapper.toCommand(contentBookInput))));
    }

    @Override
    public ResponseEntity<Void> deleteContentBook(String orgKey, UUID contentBookId) {
        deleteContentBook.execute(orgKey, contentBookId.toString());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ContentBook> replaceContentBookNodes(String orgKey, UUID contentBookId,
                                                               ContentBookNodesInput contentBookNodesInput) {
        return ResponseEntity.ok(mapper.toModel(replaceContentBookNodes.execute(orgKey, contentBookId.toString(),
                mapper.toNodes(contentBookNodesInput.getNodes()))));
    }
}
