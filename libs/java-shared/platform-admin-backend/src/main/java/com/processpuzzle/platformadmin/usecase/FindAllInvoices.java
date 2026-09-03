package com.processpuzzle.platformadmin.usecase;

import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.core.rsql.RsqlSpecificationBuilder;
import com.processpuzzle.core.rsql.SortParser;
import com.processpuzzle.platformadmin.domain.Invoice;
import com.processpuzzle.platformadmin.domain.InvoiceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Invoices across all tenants. See {@link FindAllSubscriptions} on the absent tenant specification. */
@Service
@Transactional(readOnly = true)
public class FindAllInvoices {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final InvoiceRepository repository;
    private final OrganizationGuard guard;
    private final RsqlSpecificationBuilder<Invoice> rsqlBuilder = new RsqlSpecificationBuilder<>();

    public FindAllInvoices(InvoiceRepository repository, OrganizationGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    public Page<Invoice> execute(String where, String order, Integer page, Integer size) {
        guard.requirePlatformAdmin();
        // rsqlBuilder.build returns null for an absent filter. Specification.unrestricted() rather
        // than passing that null on: every other list use case in the platform ANDs a tenant
        // predicate on and so never has a null spec, and relying on the repository's nullable-spec
        // tolerance is a needless bet on Spring Data keeping it.
        Specification<Invoice> spec = rsqlBuilder.build(where);
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
