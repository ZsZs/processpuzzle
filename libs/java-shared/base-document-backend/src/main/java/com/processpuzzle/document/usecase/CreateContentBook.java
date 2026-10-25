package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.ContentBook;
import com.processpuzzle.document.domain.ContentBookRepository;
import com.processpuzzle.document.usecase.exception.ContentBookSlugAlreadyExistsException;
import com.processpuzzle.document.usecase.service.ContentBookTreeChecker;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Any member of the organization may start a book, as any member may start a document. */
@Service
@Transactional
public class CreateContentBook {

    private final ContentBookRepository repository;
    private final ContentBookTreeChecker treeChecker;
    private final DocumentGuard guard;

    public CreateContentBook(ContentBookRepository repository, ContentBookTreeChecker treeChecker, DocumentGuard guard) {
        this.repository = repository;
        this.treeChecker = treeChecker;
        this.guard = guard;
    }

    public ContentBook execute(String orgKey, ContentBookCommand input) {
        guard.requireOrganizationAccess(orgKey);
        if (repository.existsByOrgKeyAndSlug(orgKey, input.slug())) {
            throw new ContentBookSlugAlreadyExistsException(orgKey, input.slug());
        }

        ContentBook book = new ContentBook(orgKey, UUID.randomUUID().toString(), input.slug(), input.title(),
                guard.currentPrincipal());
        input.applyTo(book);
        guard.declareRoles(orgKey, input.roles());
        if (input.nodes() != null) {
            book.replaceNodes(treeChecker.check(orgKey, input.nodes()));
        }
        return repository.save(book);
    }
}
