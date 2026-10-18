package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.ContentBook;
import com.processpuzzle.document.domain.ContentBookNode;
import com.processpuzzle.document.domain.ContentBookRepository;
import com.processpuzzle.document.usecase.exception.ContentBookNotFoundException;
import com.processpuzzle.document.usecase.service.ContentBookTreeChecker;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Replaces the whole tree. One write for any reorder, indent or removal, so the client never has to
 * order a sequence of node-level calls that could fail half-way.
 */
@Service
@Transactional
public class ReplaceContentBookNodes {

    private final ContentBookRepository repository;
    private final ContentBookTreeChecker treeChecker;
    private final DocumentGuard guard;

    public ReplaceContentBookNodes(ContentBookRepository repository, ContentBookTreeChecker treeChecker,
                                   DocumentGuard guard) {
        this.repository = repository;
        this.treeChecker = treeChecker;
        this.guard = guard;
    }

    public ContentBook execute(String orgKey, String contentBookId, List<ContentBookNode> nodes) {
        ContentBook book = repository.findByOrgKeyAndId(orgKey, contentBookId)
                .orElseThrow(() -> new ContentBookNotFoundException(orgKey, contentBookId));
        guard.requireEditor(orgKey, book.getRoles(), "content book", contentBookId);

        book.replaceNodes(treeChecker.check(orgKey, nodes));
        return repository.save(book);
    }
}
