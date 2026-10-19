package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.ContentBook;
import com.processpuzzle.document.domain.ContentBookKey;
import com.processpuzzle.document.domain.ContentBookRepository;
import com.processpuzzle.document.usecase.exception.ContentBookNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deletes the book only: its documents are independent of it and may be filed in other books. */
@Service
@Transactional
public class DeleteContentBook {

    private final ContentBookRepository repository;
    private final DocumentGuard guard;

    public DeleteContentBook(ContentBookRepository repository, DocumentGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    public void execute(String orgKey, String contentBookId) {
        ContentBook book = repository.findByOrgKeyAndId(orgKey, contentBookId)
                .orElseThrow(() -> new ContentBookNotFoundException(orgKey, contentBookId));
        guard.requireEditor(orgKey, book.getRoles(), "content book", contentBookId);

        repository.deleteById(new ContentBookKey(orgKey, contentBookId));
    }
}
