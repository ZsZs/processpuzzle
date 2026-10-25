package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.ContentBook;
import com.processpuzzle.document.domain.ContentBookRepository;
import com.processpuzzle.document.usecase.exception.ContentBookNotFoundException;
import com.processpuzzle.document.usecase.exception.ContentBookSlugAlreadyExistsException;
import com.processpuzzle.document.usecase.service.ContentBookTreeChecker;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Replaces the properties, and the tree only when the command carries one. */
@Service
@Transactional
public class UpdateContentBook {

    private final ContentBookRepository repository;
    private final ContentBookTreeChecker treeChecker;
    private final DocumentGuard guard;

    public UpdateContentBook(ContentBookRepository repository, ContentBookTreeChecker treeChecker, DocumentGuard guard) {
        this.repository = repository;
        this.treeChecker = treeChecker;
        this.guard = guard;
    }

    public ContentBook execute(String orgKey, String contentBookId, ContentBookCommand input) {
        ContentBook book = repository.findByOrgKeyAndId(orgKey, contentBookId)
                .orElseThrow(() -> new ContentBookNotFoundException(orgKey, contentBookId));
        guard.requireEditor(orgKey, book.getRoles(), "content book", contentBookId);

        if (!book.getSlug().equals(input.slug()) && repository.existsByOrgKeyAndSlug(orgKey, input.slug())) {
            throw new ContentBookSlugAlreadyExistsException(orgKey, input.slug());
        }
        input.applyTo(book);
        guard.declareRoles(orgKey, input.roles());
        if (input.nodes() != null) {
            book.replaceNodes(treeChecker.check(orgKey, input.nodes()));
        }
        return repository.save(book);
    }
}
