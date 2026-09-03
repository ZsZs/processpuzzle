package com.processpuzzle.platformadmin.usecase;

import com.processpuzzle.platformadmin.domain.Invoice;
import com.processpuzzle.platformadmin.domain.InvoiceRepository;
import com.processpuzzle.platformadmin.domain.Plan;
import com.processpuzzle.platformadmin.domain.PlanRepository;
import com.processpuzzle.platformadmin.domain.Subscription;
import com.processpuzzle.platformadmin.domain.SubscriptionRepository;
import com.processpuzzle.platformadmin.domain.UsageRecord;
import com.processpuzzle.platformadmin.domain.UsageRecordRepository;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Everything the billing screen shows for one tenant, in one round trip: the current subscription,
 * the plan it names, the usage measured in the running period, and the tenant's invoices.
 *
 * <p>One use case rather than four, because the screen renders them together and four calls would
 * make it possible to show a plan from one instant against usage from another. The whole read is in
 * one {@code readOnly} transaction for the same reason.
 *
 * <p>Every part except the tenant itself is optional, and a tenant with no subscription is a normal
 * state rather than an error: nothing in this platform creates subscriptions yet. The organization is
 * checked because a billing position for a tenant that does not exist is a 404, not an empty result —
 * an empty result would let a caller enumerate which {@code orgKey}s exist by whether the plan came
 * back null.
 */
@Service
@Transactional(readOnly = true)
public class GetOrganizationBilling {

    private final OrganizationGuard guard;
    private final com.processpuzzle.platformadmin.domain.OrganizationRepository organizations;
    private final SubscriptionRepository subscriptions;
    private final PlanRepository plans;
    private final UsageRecordRepository usage;
    private final InvoiceRepository invoices;

    public GetOrganizationBilling(OrganizationGuard guard,
                                  com.processpuzzle.platformadmin.domain.OrganizationRepository organizations,
                                  SubscriptionRepository subscriptions,
                                  PlanRepository plans,
                                  UsageRecordRepository usage,
                                  InvoiceRepository invoices) {
        this.guard = guard;
        this.organizations = organizations;
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.usage = usage;
        this.invoices = invoices;
    }

    public Result execute(String orgKey) {
        guard.requirePlatformAdmin();
        if (!organizations.existsById(orgKey)) {
            throw new OrganizationNotFoundException(orgKey);
        }

        Subscription subscription = subscriptions
                .findFirstByOrgKeyOrderByCurrentPeriodStartDesc(orgKey)
                .orElse(null);
        Plan plan = subscription == null ? null
                : plans.findById(subscription.getPlanCode()).orElse(null);
        Instant now = Instant.now();
        List<UsageRecord> usageRecords = usage
                .findByOrgKeyAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(orgKey, now, now);

        return new Result(orgKey, subscription, plan, usageRecords,
                invoices.findByOrgKeyOrderByPeriodStartDesc(orgKey));
    }

    /**
     * @param subscription the newest subscription, or {@code null} when the tenant has never had one
     * @param plan the plan that subscription names, or {@code null} when it has been withdrawn from
     *             the catalog — a subscription deliberately keeps naming a code that no longer resolves
     */
    public record Result(String orgKey, Subscription subscription, Plan plan,
                         List<UsageRecord> usage, List<Invoice> invoices) {
    }
}
