package com.processpuzzle.platformadmin.usecase;

import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.platformadmin.PlatformAdminTestFixtures;
import com.processpuzzle.platformadmin.domain.BillingInterval;
import com.processpuzzle.platformadmin.domain.Invoice;
import com.processpuzzle.platformadmin.domain.InvoiceLine;
import com.processpuzzle.platformadmin.domain.InvoiceRepository;
import com.processpuzzle.platformadmin.domain.InvoiceStatus;
import com.processpuzzle.platformadmin.domain.Plan;
import com.processpuzzle.platformadmin.domain.PlanRepository;
import com.processpuzzle.platformadmin.domain.Subscription;
import com.processpuzzle.platformadmin.domain.SubscriptionRepository;
import com.processpuzzle.platformadmin.domain.SubscriptionStatus;
import com.processpuzzle.platformadmin.domain.UsageRecordRepository;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.processpuzzle.platformadmin.PlatformAdminTestFixtures.ORG_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The read-only billing surface. Both optional halves of a billing position — no subscription at all,
 * and a subscription naming a withdrawn plan — are normal states rather than errors, and each has a
 * test here because rendering either as an error would break the screen for the majority of tenants
 * (nothing in this platform creates subscriptions yet).
 */
@SuppressWarnings("java:S5778")
class BillingQueriesTest {

    private com.processpuzzle.platformadmin.domain.OrganizationRepository organizations;
    private SubscriptionRepository subscriptions;
    private PlanRepository plans;
    private UsageRecordRepository usage;
    private InvoiceRepository invoices;

    @BeforeEach
    void setUp() {
        organizations = mock(com.processpuzzle.platformadmin.domain.OrganizationRepository.class);
        subscriptions = mock(SubscriptionRepository.class);
        plans = mock(PlanRepository.class);
        usage = mock(UsageRecordRepository.class);
        invoices = mock(InvoiceRepository.class);
        when(organizations.existsById(ORG_KEY)).thenReturn(true);
        when(usage.findByOrgKeyAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
                anyString(), any(), any())).thenReturn(List.of());
        when(invoices.findByOrgKeyOrderByPeriodStartDesc(anyString())).thenReturn(List.of());
    }

    @Test
    void returnsTheSubscriptionTogetherWithThePlanItNames() {
        when(subscriptions.findFirstByOrgKeyOrderByCurrentPeriodStartDesc(ORG_KEY))
                .thenReturn(Optional.of(subscription("team")));
        when(plans.findById("team")).thenReturn(Optional.of(plan("team")));

        GetOrganizationBilling.Result result =
                billing(PlatformAdminTestFixtures.permissiveGuard()).execute(ORG_KEY);

        assertThat(result.orgKey()).isEqualTo(ORG_KEY);
        assertThat(result.subscription()).isNotNull();
        assertThat(result.plan()).isNotNull().extracting(Plan::getCode).isEqualTo("team");
    }

    /**
     * Invoices are read even when there is no subscription: a cancelled tenant has no current
     * subscription and still has a billing history, and losing it here would lose it from the screen.
     */
    @Test
    void invoicesAreReturnedIndependentlyOfTheSubscription() {
        when(subscriptions.findFirstByOrgKeyOrderByCurrentPeriodStartDesc(ORG_KEY))
                .thenReturn(Optional.empty());
        when(invoices.findByOrgKeyOrderByPeriodStartDesc(ORG_KEY)).thenReturn(List.of(invoice()));

        GetOrganizationBilling.Result result =
                billing(PlatformAdminTestFixtures.permissiveGuard()).execute(ORG_KEY);

        assertThat(result.subscription()).isNull();
        assertThat(result.invoices()).singleElement()
                .satisfies(one -> assertThat(one.getLines()).hasSize(1));
    }

    @Test
    void aTenantWithNoSubscriptionIsAnEmptyPositionRatherThanAnError() {
        when(subscriptions.findFirstByOrgKeyOrderByCurrentPeriodStartDesc(ORG_KEY))
                .thenReturn(Optional.empty());

        GetOrganizationBilling.Result result =
                billing(PlatformAdminTestFixtures.permissiveGuard()).execute(ORG_KEY);

        assertThat(result.subscription()).isNull();
        assertThat(result.plan()).isNull();
        assertThat(result.usage()).isEmpty();
        assertThat(result.invoices()).isEmpty();
    }

    /**
     * A subscription deliberately keeps naming a plan code even after the plan leaves the catalog —
     * a foreign key would have forced the history to be rewritten instead.
     */
    @Test
    void aSubscriptionNamingAWithdrawnPlanStillReadsBack() {
        when(subscriptions.findFirstByOrgKeyOrderByCurrentPeriodStartDesc(ORG_KEY))
                .thenReturn(Optional.of(subscription("legacy-2019")));
        when(plans.findById("legacy-2019")).thenReturn(Optional.empty());

        GetOrganizationBilling.Result result =
                billing(PlatformAdminTestFixtures.permissiveGuard()).execute(ORG_KEY);

        assertThat(result.subscription()).isNotNull();
        assertThat(result.plan()).isNull();
    }

    /**
     * 404 rather than an empty position, so a caller cannot enumerate which {@code orgKey}s exist by
     * whether the plan came back null.
     */
    @Test
    void anUnknownTenantIs404RatherThanAnEmptyPosition() {
        when(organizations.existsById("nope")).thenReturn(false);

        assertThatThrownBy(() -> billing(PlatformAdminTestFixtures.permissiveGuard()).execute("nope"))
                .isInstanceOf(OrganizationNotFoundException.class);

        verifyNoInteractions(subscriptions, plans, usage, invoices);
    }

    @Test
    void everyBillingQueryRequiresStaffAuthority() {
        OrganizationGuard denied = PlatformAdminTestFixtures.denyingGuard();

        assertThatThrownBy(() -> billing(denied).execute(ORG_KEY))
                .isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> new FindAllPlans(plans, denied).execute(null, null))
                .isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> new FindAllSubscriptions(subscriptions, denied).execute(null, null, null, null))
                .isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> new FindAllInvoices(invoices, denied).execute(null, null, null, null))
                .isInstanceOf(OrganizationAccessDeniedException.class);

        verifyNoInteractions(organizations, subscriptions, plans, usage, invoices);
    }

    private GetOrganizationBilling billing(OrganizationGuard guard) {
        return new GetOrganizationBilling(guard, organizations, subscriptions, plans, usage, invoices);
    }

    private static Subscription subscription(String planCode) {
        return new Subscription("sub-1", ORG_KEY, planCode, SubscriptionStatus.ACTIVE,
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"));
    }

    private static Plan plan(String code) {
        return new Plan(code, "Team", "For a working team.", BillingInterval.MONTHLY, "EUR", 4900L, List.of());
    }

    private static Invoice invoice() {
        return new Invoice("inv-1", ORG_KEY,
                new Invoice.Details("2026-0001", InvoiceStatus.PAID, "EUR", 4900L,
                        Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                        List.of(new InvoiceLine("Team plan, August", null, 1L, 4900L, 4900L))));
    }
}
