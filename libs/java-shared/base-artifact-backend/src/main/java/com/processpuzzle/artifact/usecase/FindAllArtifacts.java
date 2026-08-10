package com.processpuzzle.artifact.usecase;

import com.processpuzzle.artifact.domain.Artifact;
import com.processpuzzle.artifact.domain.ArtifactRepository;
import com.processpuzzle.core.rsql.RsqlSpecificationBuilder;
import com.processpuzzle.core.rsql.SortParser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FindAllArtifacts {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final ArtifactRepository repository;
    private final RsqlSpecificationBuilder<Artifact> rsqlBuilder = new RsqlSpecificationBuilder<>();

    public FindAllArtifacts(ArtifactRepository repository) {
        this.repository = repository;
    }

    /**
     * The tenant specification is ANDed first and is never optional — same reasoning
     * {@code FindAllRules} documents: RSQL permits a top-level OR, which would otherwise
     * escape the filter and return other organizations' artifacts.
     */
    public Page<Artifact> execute(String orgKey, String where, String order, Integer page, Integer size) {
        Specification<Artifact> spec = orgKeySpec(orgKey);
        Specification<Artifact> whereSpec = rsqlBuilder.build(where);
        if (whereSpec != null) {
            spec = spec.and(whereSpec);
        }
        Sort sort = SortParser.parse(order);
        Pageable pageable = PageRequest.of(page != null ? page : DEFAULT_PAGE, size != null ? size : DEFAULT_SIZE, sort);
        return repository.findAll(spec, pageable);
    }

    private static Specification<Artifact> orgKeySpec(String orgKey) {
        return (root, query, cb) -> cb.equal(root.get("orgKey"), orgKey);
    }
}
