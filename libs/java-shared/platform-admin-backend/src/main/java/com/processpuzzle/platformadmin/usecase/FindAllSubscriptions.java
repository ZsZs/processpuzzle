package com.processpuzzle.platformadmin.usecase;

import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.core.rsql.RsqlSpecificationBuilder;
import com.processpuzzle.core.rsql.SortParser;
import com.processpuzzle.platformadmin.domain.Subscription;
import com.processpuzzle.platformadmin.domain.SubscriptionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subscriptions across all tenants, for the platform's own revenue view. No tenant specification, for
 * the same reason as {@link FindAllOrganizations} — {@code requirePlatformAdmin} is what stands in
 * for one. A tenant filters its own by passing {@code orgKey=="..."} in the RSQL.
 */
@Service
@Transactional(readOnly = true)
public class FindAllSubscriptions {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final SubscriptionRepository repository;
    private final OrganizationGuard guard;
    private final RsqlSpecificationBuilder<Subscription> rsqlBuilder = new RsqlSpecificationBuilder<>();

    public FindAllSubscriptions(SubscriptionRepository repository, OrganizationGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    public Page<Subscription> execute(String where, String order, Integer page, Integer size) {
        guard.requirePlatformAdmin();
        // rsqlBuilder.build returns null for an absent filter. Specification.unrestricted() rather
        // than passing that null on: every other list use case in the platform ANDs a tenant
        // predicate on and so never has a null spec, and relying on the repository's nullable-spec
        // tolerance is a needless bet on Spring Data keeping it.
        Specification<Subscription> spec = rsqlBuilder.build(where);
        if (spec == null) {
            spec = Specification.unrestricted();
        }
        Pageable pageable = PageRequest.of(
                page != null ? page : DEFAULT_PAGE,
                size != null ? size : DEFAULT_SIZE,
                SortParser.parse(order));
        return repository.findAll(spec, pageable);
    }
}
