package com.processpuzzle.document.usecase;

import com.processpuzzle.core.rsql.RsqlSpecificationBuilder;
import com.processpuzzle.core.rsql.SortParser;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class FindAllDocuments {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final DocumentRepository repository;
    private final DocumentTranslationAssembler assembler;
    private final DocumentGuard guard;
    private final RsqlSpecificationBuilder<Document> rsqlBuilder = new RsqlSpecificationBuilder<>();

    public FindAllDocuments(DocumentRepository repository, DocumentTranslationAssembler assembler, DocumentGuard guard) {
        this.repository = repository;
        this.assembler = assembler;
        this.guard = guard;
    }

    /**
     * The tenant specification is ANDed first and is never optional — same reasoning
     * {@code FindAllRules} documents: RSQL permits a top-level OR, which would otherwise
     * escape the filter and return other organizations' documents.
     *
     * <p>Reader-role filtering happens after the query rather than inside it, because the roles are
     * a JSON column and the decision belongs to the policy port rather than to SQL. The visible
     * consequence is that a page can come back shorter than {@code size} — accepted deliberately:
     * the alternative is either leaking restricted titles or over-fetching to refill pages, and
     * both are worse than a short page in a designer's list.
     */
    public Result execute(String orgKey, String where, String order, Integer page, Integer size) {
        guard.requireOrganizationAccess(orgKey);

        Specification<Document> spec = orgKeySpec(orgKey);
        Specification<Document> whereSpec = rsqlBuilder.build(where);
        if (whereSpec != null) {
            spec = spec.and(whereSpec);
        }
        Sort sort = SortParser.parse(order);
        Pageable pageable = PageRequest.of(page != null ? page : DEFAULT_PAGE, size != null ? size : DEFAULT_SIZE, sort);

        Page<Document> found = repository.findAll(spec, pageable);
        List<Document> readable = found.getContent().stream().filter(guard::canRead).toList();

        Map<String, List<DocumentTranslationView>> states = new LinkedHashMap<>();
        for (Document document : readable) {
            states.put(document.getId(), assembler.statesOf(document));
        }
        return new Result(new PageImpl<>(readable, pageable, found.getTotalElements()), states);
    }

    /** The page plus, per document id, the publication state of each of its locales. */
    public record Result(Page<Document> page, Map<String, List<DocumentTranslationView>> statesByDocumentId) {
    }

    private static Specification<Document> orgKeySpec(String orgKey) {
        return (root, query, cb) -> cb.equal(root.get("orgKey"), orgKey);
    }
}
