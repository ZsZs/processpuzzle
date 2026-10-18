package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.ContentBook;
import com.processpuzzle.document.domain.ContentBookRepository;
import com.processpuzzle.document.usecase.exception.ContentBookNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FindContentBook {

    private final ContentBookRepository repository;
    private final DocumentGuard guard;

    public FindContentBook(ContentBookRepository repository, DocumentGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    /** 404 rather than 403 for a principal who may not read the book, as {@link FindDocument} answers. */
    public ContentBook execute(String orgKey, String contentBookId) {
        ContentBook book = repository.findByOrgKeyAndId(orgKey, contentBookId)
                .orElseThrow(() -> new ContentBookNotFoundException(orgKey, contentBookId));
        if (!guard.canRead(orgKey, book.isPublic(), book.getRoles())) {
            throw new ContentBookNotFoundException(orgKey, contentBookId);
        }
        return book;
    }
}
