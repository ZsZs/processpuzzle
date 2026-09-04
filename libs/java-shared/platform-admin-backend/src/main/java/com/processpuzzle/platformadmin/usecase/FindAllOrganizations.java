package com.processpuzzle.platformadmin.usecase;

import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.core.rsql.RsqlSpecificationBuilder;
import com.processpuzzle.core.rsql.SortParser;
import com.processpuzzle.platformadmin.domain.Organization;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists every organization on the platform.
 *
 * <p>The one query in this module with no tenant specification, and the only place that is correct:
 * every other list endpoint in ProcessPuzzle ANDs an {@code orgKey} predicate onto the caller's RSQL
 * precisely so a crafted {@code where} cannot widen past the tenant boundary. Here there is no tenant
 * to stay inside — the whole point is to see across them — so the RSQL is used as given and
 * {@link OrganizationGuard#requirePlatformAdmin()} is what stands in for that predicate. Getting that
 * gate wrong would expose the platform's entire customer list, so it is checked before anything else
 * happens.
 */
@Service
@Transactional(readOnly = true)
public class FindAllOrganizations {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final OrganizationRepository repository;
    private final OrganizationGuard guard;
    private final RsqlSpecificationBuilder<Organization> rsqlBuilder = new RsqlSpecificationBuilder<>();

    public FindAllOrganizations(OrganizationRepository repository, OrganizationGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    public Page<Organization> execute(String where, String order, Integer page, Integer size) {
        guard.requirePlatformAdmin();

        // rsqlBuilder.build returns null for an absent filter. Specification.unrestricted() rather
        // than passing that null on: every other list use case in the platform ANDs a tenant
        // predicate on and so never has a null spec, and relying on the repository's nullable-spec
        // tolerance is a needless bet on Spring Data keeping it.
        Specification<Organization> spec = rsqlBuilder.build(where);
        if (spec == null) {
            spec = Specification.unrestricted();
        }
        Sort sort = SortParser.parse(order);
        Pageable pageable = PageRequest.of(
                page != null ? page : DEFAULT_PAGE,
                size != null ? size : DEFAULT_SIZE,
                sort);
        return repository.findAll(spec, pageable);
    }
}
