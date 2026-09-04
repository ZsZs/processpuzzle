package com.processpuzzle.platformadmin.usecase;

import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.core.rsql.RsqlSpecificationBuilder;
import com.processpuzzle.core.rsql.SortParser;
import com.processpuzzle.platformadmin.domain.Plan;
import com.processpuzzle.platformadmin.domain.PlanRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The plan catalog. Unpaged, unlike the other list use cases here: a catalog with enough entries to
 * need paging would be a pricing problem rather than an API one, and the plan picker renders all of
 * them at once.
 */
@Service
@Transactional(readOnly = true)
public class FindAllPlans {

    private final PlanRepository repository;
    private final OrganizationGuard guard;
    private final RsqlSpecificationBuilder<Plan> rsqlBuilder = new RsqlSpecificationBuilder<>();

    public FindAllPlans(PlanRepository repository, OrganizationGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    public List<Plan> execute(String where, String order) {
        guard.requirePlatformAdmin();
        // rsqlBuilder.build returns null for an absent filter. Specification.unrestricted() rather
        // than passing that null on: every other list use case in the platform ANDs a tenant
        // predicate on and so never has a null spec, and relying on the repository's nullable-spec
        // tolerance is a needless bet on Spring Data keeping it.
        Specification<Plan> spec = rsqlBuilder.build(where);
        if (spec == null) {
            spec = Specification.unrestricted();
        }
        return repository.findAll(spec, SortParser.parse(order));
    }
}
