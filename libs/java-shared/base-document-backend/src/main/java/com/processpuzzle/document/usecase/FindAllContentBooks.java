package com.processpuzzle.document.usecase;

import com.processpuzzle.core.rsql.RsqlSpecificationBuilder;
import com.processpuzzle.core.rsql.SortParser;
import com.processpuzzle.document.domain.ContentBook;
import com.processpuzzle.document.domain.ContentBookRepository;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Same shape as {@link FindAllDocuments}: the tenant specification is ANDed first and never optional,
 * and reader-role filtering happens after the query, so a page can come back short for the reason
 * given there.
 */
@Service
@Transactional(readOnly = true)
public class FindAllContentBooks {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final ContentBookRepository repository;
    private final DocumentGuard guard;
    private final RsqlSpecificationBuilder<ContentBook> rsqlBuilder = new RsqlSpecificationBuilder<>();

    public FindAllContentBooks(ContentBookRepository repository, DocumentGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    public Page<ContentBook> execute(String orgKey, String where, String order, Integer page, Integer size) {
        guard.requireOrganizationAccess(orgKey);

        Specification<ContentBook> spec = (root, query, cb) -> cb.equal(root.get("orgKey"), orgKey);
        Specification<ContentBook> whereSpec = rsqlBuilder.build(where);
        if (whereSpec != null) {
            spec = spec.and(whereSpec);
        }
        Pageable pageable = PageRequest.of(page != null ? page : DEFAULT_PAGE, size != null ? size : DEFAULT_SIZE,
                SortParser.parse(order));

        Page<ContentBook> found = repository.findAll(spec, pageable);
        List<ContentBook> readable = found.getContent().stream()
                .filter(book -> guard.canRead(book.getOrgKey(), book.isPublic(), book.getRoles()))
                .toList();
        return new PageImpl<>(readable, pageable, found.getTotalElements());
    }
}
