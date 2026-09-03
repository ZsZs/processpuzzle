package com.processpuzzle.platformadmin.adapter.inbound;

import com.processpuzzle.platformadmin.PlatformAdminTestFixtures;
import com.processpuzzle.platformadmin.domain.BillingInterval;
import com.processpuzzle.platformadmin.domain.Invoice;
import com.processpuzzle.platformadmin.domain.InvoiceLine;
import com.processpuzzle.platformadmin.domain.InvoiceStatus;
import com.processpuzzle.platformadmin.domain.Plan;
import com.processpuzzle.platformadmin.domain.PlanLimit;
import com.processpuzzle.platformadmin.domain.Subscription;
import com.processpuzzle.platformadmin.domain.SubscriptionStatus;
import com.processpuzzle.platformadmin.domain.UsageMetric;
import com.processpuzzle.platformadmin.domain.UsageRecord;
import com.processpuzzle.platformadmin.model.AdminUserInput;
import com.processpuzzle.platformadmin.model.OrganizationUpdate;
import com.processpuzzle.platformadmin.usecase.AssignOrganizationAdmin;
import com.processpuzzle.platformadmin.usecase.GetOrganizationBilling;
import com.processpuzzle.platformadmin.usecase.OrganizationDetails;
import com.processpuzzle.platformadmin.usecase.port.IdentityRealmPort;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAdminMapperTest {

    private static final Instant PERIOD_START = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant PERIOD_END = Instant.parse("2026-09-01T00:00:00Z");

    private final PlatformAdminMapper mapper = new PlatformAdminMapper();

    @Test
    void anOrganizationCarriesEveryDescriptiveFieldAndItsStatus() {
        var model = mapper.toModel(PlatformAdminTestFixtures.organization());

        assertThat(model.getKey()).isEqualTo(PlatformAdminTestFixtures.ORG_KEY);
        assertThat(model.getName()).isEqualTo("My Organization Ltd.");
        assertThat(model.getDescription()).isEqualTo("Insurance.");
        assertThat(model.getContactEmail()).isEqualTo("ops@my-org.example");
        assertThat(model.getDefaultLocale()).isEqualTo("en-GB");
        assertThat(model.getStatus().getValue()).isEqualTo("ACTIVE");
    }

    /** Only a persisted entity has timestamps; an unsaved one must map to nulls, not to an epoch. */
    @Test
    void absentTimestampsMapToNullRatherThanToAnEpoch() {
        var model = mapper.toModel(PlatformAdminTestFixtures.organization());

        assertThat(model.getCreatedAt()).isNull();
        assertThat(model.getUpdatedAt()).isNull();
    }

    @Test
    void anOrganizationPageCarriesItsPagingMetadata() {
        var page = mapper.toOrganizationPage(
                new PageImpl<>(List.of(PlatformAdminTestFixtures.organization())));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(1L);
        assertThat(page.getTotalPages()).isEqualTo(1);
        assertThat(page.getNumber()).isZero();
    }

    @Test
    void anUpdatePayloadBecomesTheUseCaseLevelRecord() {
        OrganizationUpdate input = new OrganizationUpdate("Renamed");
        input.setDescription("Now German.");
        input.setContactEmail("ops@my-org.example");
        input.setDefaultLocale("de-DE");

        assertThat(mapper.toDetails(input)).isEqualTo(
                new OrganizationDetails("Renamed", "Now German.", "ops@my-org.example", "de-DE"));
    }

    @Test
    void anAdminUserInputBecomesTheRealmPortsNewUser() {
        AdminUserInput input = new AdminUserInput("ada", "ada@my-org.example");
        input.setFirstName("Ada");
        input.setLastName("Lovelace");

        assertThat(mapper.toNewUser(input)).isEqualTo(
                new IdentityRealmPort.NewUser("ada", "ada@my-org.example", "Ada", "Lovelace"));
    }

    @Test
    void aCreatedAdministratorEchoesBackItsRealmAndRoles() {
        var result = new AssignOrganizationAdmin.Result("kc-1", "my-org",
                new IdentityRealmPort.NewUser("ada", "ada@my-org.example", "Ada", "Lovelace"),
                List.of("org-admin", "org-member"));

        var model = mapper.toModel(result);

        assertThat(model.getId()).isEqualTo("kc-1");
        assertThat(model.getRealm()).isEqualTo("my-org");
        assertThat(model.getUsername()).isEqualTo("ada");
        assertThat(model.getRoles()).containsExactly("org-admin", "org-member");
    }

    @Test
    void aPlanCarriesItsPriceInMinorUnitsAndItsLimits() {
        var model = mapper.toModel(new Plan("team", "Team", "For a working team.",
                BillingInterval.MONTHLY, "EUR", 4900L,
                List.of(new PlanLimit(UsageMetric.USERS, 25L))));

        assertThat(model.getAmountMinor()).isEqualTo(4900L);
        assertThat(model.getCurrency()).isEqualTo("EUR");
        assertThat(model.getInterval().getValue()).isEqualTo("MONTHLY");
        assertThat(model.getLimits()).singleElement().satisfies(limit -> {
            assertThat(limit.getMetric().getValue()).isEqualTo("USERS");
            assertThat(limit.getMaxQuantity()).isEqualTo(25L);
        });
    }

    @Test
    void instantsMapOntoTheContractAsUtcOffsets() {
        var model = mapper.toModel(new Subscription("sub-1", "my-org", "team",
                SubscriptionStatus.ACTIVE, PERIOD_START, PERIOD_END));

        assertThat(model.getCurrentPeriodStart().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(model.getCurrentPeriodStart().toInstant()).isEqualTo(PERIOD_START);
        assertThat(model.getCanceledAt()).isNull();
    }

    @Test
    void aUsageRecordCarriesItsMetricAndQuantity() {
        var model = mapper.toModel(new UsageRecord("u-1", "my-org", UsageMetric.API_CALLS, 4200L,
                PERIOD_START, PERIOD_END));

        assertThat(model.getMetric().getValue()).isEqualTo("API_CALLS");
        assertThat(model.getQuantity()).isEqualTo(4200L);
    }

    /**
     * A flat plan fee has no metric, and {@code UsageMetric.fromValue(null)} throws — so the line
     * mapper has to skip the field rather than convert it unconditionally.
     */
    @Test
    void anInvoiceLineWithoutAMetricMapsWithoutOne() {
        var model = mapper.toModel(invoice(new InvoiceLine("Team plan, August", null, 1L, 4900L, 4900L)));

        assertThat(model.getLines()).singleElement()
                .satisfies(line -> assertThat(line.getMetric()).isNull());
    }

    @Test
    void anInvoiceLineWithAMetricKeepsIt() {
        var model = mapper.toModel(invoice(
                new InvoiceLine("Overage", UsageMetric.API_CALLS, 200L, 1L, 200L)));

        assertThat(model.getLines()).singleElement().satisfies(line ->
                assertThat(line.getMetric().getValue()).isEqualTo("API_CALLS"));
    }

    /** Both optional halves absent is the common case today: nothing creates subscriptions yet. */
    @Test
    void aBillingPositionWithoutASubscriptionOmitsBothItAndThePlan() {
        var model = mapper.toModel(new GetOrganizationBilling.Result(
                "my-org", null, null, List.of(), List.of()));

        assertThat(model.getOrgKey()).isEqualTo("my-org");
        assertThat(model.getSubscription()).isNull();
        assertThat(model.getPlan()).isNull();
        assertThat(model.getUsage()).isEmpty();
        assertThat(model.getInvoices()).isEmpty();
    }

    @Test
    void aBillingPositionCarriesTheSubscriptionThePlanTheUsageAndTheInvoices() {
        var model = mapper.toModel(new GetOrganizationBilling.Result(
                "my-org",
                new Subscription("sub-1", "my-org", "team", SubscriptionStatus.ACTIVE,
                        PERIOD_START, PERIOD_END),
                new Plan("team", "Team", null, BillingInterval.MONTHLY, "EUR", 4900L, List.of()),
                List.of(new UsageRecord("u-1", "my-org", UsageMetric.USERS, 4L, PERIOD_START, PERIOD_END)),
                List.of(invoice(new InvoiceLine("Team plan", null, 1L, 4900L, 4900L)))));

        assertThat(model.getSubscription()).isNotNull();
        assertThat(model.getPlan()).isNotNull();
        assertThat(model.getUsage()).hasSize(1);
        assertThat(model.getInvoices()).hasSize(1);
    }

    @Test
    void subscriptionAndInvoicePagesCarryTheirPagingMetadata() {
        var subscriptions = mapper.toSubscriptionPage(new PageImpl<>(List.of(
                new Subscription("sub-1", "my-org", "team", SubscriptionStatus.ACTIVE,
                        PERIOD_START, PERIOD_END))));
        var invoices = mapper.toInvoicePage(new PageImpl<>(List.of(invoice())));

        assertThat(subscriptions.getContent()).hasSize(1);
        assertThat(subscriptions.getTotalElements()).isEqualTo(1L);
        assertThat(invoices.getContent()).hasSize(1);
        assertThat(invoices.getSize()).isEqualTo(1);
    }

    @Test
    void thePlanListMapsEveryEntry() {
        assertThat(mapper.toPlanList(List.of(
                new Plan("free", "Free", null, BillingInterval.MONTHLY, "EUR", 0L, List.of()),
                new Plan("team", "Team", null, BillingInterval.MONTHLY, "EUR", 4900L, List.of()))))
                .extracting(com.processpuzzle.platformadmin.model.Plan::getCode)
                .containsExactly("free", "team");
    }

    private static Invoice invoice(InvoiceLine... lines) {
        return new Invoice("inv-1", "my-org",
                new Invoice.Details("2026-0001", InvoiceStatus.PAID, "EUR", 4900L,
                        PERIOD_START, PERIOD_END, List.of(lines)));
    }
}
