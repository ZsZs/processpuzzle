package com.processpuzzle.platformadmin.domain;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the billing mapping binds before anything is built on it. Two things here are the kind that
 * pass review and fail at runtime: the {@code @ElementCollection} of {@code @Embeddable} limits and
 * invoice lines, and the derived query names — a typo in
 * {@code findByOrgKeyAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual} is a startup failure
 * rather than a compile error, so only running against a database catches it.
 */
@DataJpaTest(showSql = false)
@EntityScan("com.processpuzzle.platformadmin.domain")
@EnableJpaRepositories("com.processpuzzle.platformadmin.domain")
class BillingPersistenceTest {

    private static final Instant PERIOD_START = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant PERIOD_END = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant MID_PERIOD = Instant.parse("2026-08-15T00:00:00Z");
    private static final long ONE_MONTH_SECONDS = 2_592_000L;

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private PlanRepository plans;

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private UsageRecordRepository usage;

    @Autowired
    private InvoiceRepository invoices;

    @Test
    void aPlansLimitsRoundTripThroughTheElementCollection() {
        plans.saveAndFlush(new Plan("team", "Team", "For a working team.", BillingInterval.MONTHLY,
                "EUR", 4900L, List.of(
                        new PlanLimit(UsageMetric.USERS, 25L),
                        new PlanLimit(UsageMetric.API_CALLS, 1_000_000L))));

        Optional<Plan> reloaded = plans.findById("team");

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getAmountMinor()).isEqualTo(4900L);
        assertThat(reloaded.get().getLimits())
                .extracting(PlanLimit::getMetric, PlanLimit::getMaxQuantity)
                .containsExactlyInAnyOrder(
                        Tuple.tuple(UsageMetric.USERS, 25L),
                        Tuple.tuple(UsageMetric.API_CALLS, 1_000_000L));
    }

    /** An unmetered plan stores no rows in the limits table rather than a row meaning "unlimited". */
    @Test
    void aPlanWithNoLimitsRoundTripsAsEmptyRatherThanNull() {
        plans.saveAndFlush(new Plan("enterprise", "Enterprise", null, BillingInterval.YEARLY,
                "EUR", 990_000L, List.of()));

        assertThat(plans.findById("enterprise")).isPresent()
                .get().extracting(Plan::getLimits)
                .asInstanceOf(InstanceOfAssertFactories.list(PlanLimit.class))
                .isEmpty();
    }

    /**
     * A tenant that changes plan mid-term has two rows and the newest is the one in force — which is
     * why {@code orgKey} carries no uniqueness constraint.
     */
    @Test
    void theCurrentSubscriptionIsTheOneWithTheLatestPeriodStart() {
        subscriptions.saveAndFlush(new Subscription("old", "my-org", "free",
                SubscriptionStatus.CANCELED, PERIOD_START.minusSeconds(ONE_MONTH_SECONDS), PERIOD_START));
        subscriptions.saveAndFlush(new Subscription("current", "my-org", "team",
                SubscriptionStatus.ACTIVE, PERIOD_START, PERIOD_END));

        assertThat(subscriptions.findFirstByOrgKeyOrderByCurrentPeriodStartDesc("my-org"))
                .isPresent()
                .get()
                .extracting(Subscription::getId)
                .isEqualTo("current");
    }

    @Test
    void aSubscriptionGetsItsTimestampsOnPersist() {
        subscriptions.saveAndFlush(new Subscription("sub-1", "my-org", "team",
                SubscriptionStatus.TRIALING, PERIOD_START, PERIOD_END));

        assertThat(subscriptions.findById("sub-1")).isPresent().get().satisfies(subscription -> {
            assertThat(subscription.getCreatedAt()).isNotNull();
            assertThat(subscription.getUpdatedAt()).isNotNull();
            assertThat(subscription.getCanceledAt()).isNull();
        });
    }

    @Test
    void usageIsFoundByTheInstantFallingInsideItsPeriod() {
        usage.saveAndFlush(new UsageRecord("u-current", "my-org", UsageMetric.USERS, 4L,
                PERIOD_START, PERIOD_END));
        usage.saveAndFlush(new UsageRecord("u-past", "my-org", UsageMetric.USERS, 3L,
                PERIOD_START.minusSeconds(ONE_MONTH_SECONDS), PERIOD_START.minusSeconds(1)));

        assertThat(usage.findByOrgKeyAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
                "my-org", MID_PERIOD, MID_PERIOD))
                .extracting(UsageRecord::getId).containsExactly("u-current");
    }

    @Test
    void usageIsScopedToItsOwnTenant() {
        usage.saveAndFlush(new UsageRecord("mine", "my-org", UsageMetric.USERS, 4L,
                PERIOD_START, PERIOD_END));
        usage.saveAndFlush(new UsageRecord("theirs", "other-org", UsageMetric.USERS, 9L,
                PERIOD_START, PERIOD_END));

        assertThat(usage.findByOrgKeyAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
                "my-org", MID_PERIOD, MID_PERIOD))
                .extracting(UsageRecord::getId).containsExactly("mine");
    }

    @Test
    void anInvoicesLinesRoundTripAndItIsListedNewestPeriodFirst() {
        invoices.saveAndFlush(new Invoice("inv-jul", "my-org",
                new Invoice.Details("2026-0001", InvoiceStatus.PAID, "EUR",
                        4900L, PERIOD_START.minusSeconds(ONE_MONTH_SECONDS), PERIOD_START,
                        List.of(new InvoiceLine("Team plan, July", null, 1L, 4900L, 4900L)))));
        invoices.saveAndFlush(new Invoice("inv-aug", "my-org",
                new Invoice.Details(null, InvoiceStatus.DRAFT, "EUR", 5100L, PERIOD_START, PERIOD_END,
                        List.of(new InvoiceLine("Team plan, August", null, 1L, 4900L, 4900L),
                                new InvoiceLine("API overage", UsageMetric.API_CALLS, 200L, 1L, 200L)))));

        List<Invoice> found = invoices.findByOrgKeyOrderByPeriodStartDesc("my-org");

        assertThat(found).extracting(Invoice::getId).containsExactly("inv-aug", "inv-jul");
        assertThat(found.getFirst().getLines()).hasSize(2)
                .extracting(InvoiceLine::getMetric)
                .containsExactlyInAnyOrder(null, UsageMetric.API_CALLS);
    }

    /** A DRAFT has an identity but no number: numbers must be gapless once issued. */
    @Test
    void aDraftInvoiceRoundTripsWithoutANumber() {
        invoices.saveAndFlush(new Invoice("draft", "my-org",
                new Invoice.Details(null, InvoiceStatus.DRAFT, "EUR", 0L, PERIOD_START, PERIOD_END,
                        List.of())));

        assertThat(invoices.findById("draft")).isPresent().get().satisfies(invoice -> {
            assertThat(invoice.getNumber()).isNull();
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
            assertThat(invoice.getLines()).isEmpty();
        });
    }
}
