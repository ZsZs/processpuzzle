package com.processpuzzle.platformadmin.adapter.inbound;

import com.processpuzzle.platformadmin.model.AdminUser;
import com.processpuzzle.platformadmin.model.AdminUserInput;
import com.processpuzzle.platformadmin.model.BillingInterval;
import com.processpuzzle.platformadmin.model.Invoice;
import com.processpuzzle.platformadmin.model.InvoiceLine;
import com.processpuzzle.platformadmin.model.InvoiceStatus;
import com.processpuzzle.platformadmin.model.Organization;
import com.processpuzzle.platformadmin.model.OrganizationBilling;
import com.processpuzzle.platformadmin.model.OrganizationStatus;
import com.processpuzzle.platformadmin.model.OrganizationUpdate;
import com.processpuzzle.platformadmin.model.PageOfInvoice;
import com.processpuzzle.platformadmin.model.PageOfOrganization;
import com.processpuzzle.platformadmin.model.PageOfSubscription;
import com.processpuzzle.platformadmin.model.Plan;
import com.processpuzzle.platformadmin.model.PlanLimit;
import com.processpuzzle.platformadmin.model.Subscription;
import com.processpuzzle.platformadmin.model.SubscriptionStatus;
import com.processpuzzle.platformadmin.model.UsageMetric;
import com.processpuzzle.platformadmin.model.UsageRecord;
import com.processpuzzle.platformadmin.usecase.AssignOrganizationAdmin;
import com.processpuzzle.platformadmin.usecase.GetOrganizationBilling;
import com.processpuzzle.platformadmin.usecase.OrganizationDetails;
import com.processpuzzle.platformadmin.usecase.port.IdentityRealmPort;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Domain to contract, almost entirely one way: the staff surface is nearly all reads, so only
 * {@link #toDetails} and {@link #toNewUser} go the other direction.
 *
 * <p>Enum conversions go through {@code fromValue(name())} rather than a switch. Each enum is
 * declared twice — once in the domain, once by the generator from the contract — and
 * {@code fromValue} throws if the two ever diverge, where a switch with a default arm would quietly
 * map a newly added status onto an old one.
 *
 * <p>Note the two {@code Organization} projections in this repository are structurally identical but
 * separate types: this one and base-app's. Deliberate — base-app's is the tenant-facing view its own
 * contract declares, this one is the staff view, and mapping them onto one generated class would tie
 * the two contracts together for no gain. Each mapper builds its own.
 */
@Component
public class PlatformAdminMapper {

    // --- organizations -------------------------------------------------------------------

    public Organization toModel(com.processpuzzle.platformadmin.domain.Organization organization) {
        Organization model = new Organization(
                organization.getKey(),
                organization.getName(),
                OrganizationStatus.fromValue(organization.getStatus().name()));
        model.setDescription(organization.getDescription());
        model.setContactEmail(organization.getContactEmail());
        model.setDefaultLocale(organization.getDefaultLocale());
        model.setCreatedAt(toOffsetDateTime(organization.getCreatedAt()));
        model.setUpdatedAt(toOffsetDateTime(organization.getUpdatedAt()));
        return model;
    }

    public PageOfOrganization toOrganizationPage(
            Page<com.processpuzzle.platformadmin.domain.Organization> page) {
        PageOfOrganization model = new PageOfOrganization();
        model.setContent(page.getContent().stream().map(this::toModel).toList());
        model.setTotalElements(page.getTotalElements());
        model.setTotalPages(page.getTotalPages());
        model.setNumber(page.getNumber());
        model.setSize(page.getSize());
        return model;
    }

    public OrganizationDetails toDetails(OrganizationUpdate input) {
        return new OrganizationDetails(input.getName(), input.getDescription(),
                input.getContactEmail(), input.getDefaultLocale());
    }

    // --- the tenant's administrator -------------------------------------------------------

    public IdentityRealmPort.NewUser toNewUser(AdminUserInput input) {
        return new IdentityRealmPort.NewUser(
                input.getUsername(), input.getEmail(), input.getFirstName(), input.getLastName());
    }

    public AdminUser toModel(AssignOrganizationAdmin.Result result) {
        AdminUser model = new AdminUser(result.userId(), result.user().username(), result.realm());
        model.setEmail(result.user().email());
        model.setFirstName(result.user().firstName());
        model.setLastName(result.user().lastName());
        model.setRoles(result.roles());
        return model;
    }

    // --- billing --------------------------------------------------------------------------

    public Plan toModel(com.processpuzzle.platformadmin.domain.Plan plan) {
        Plan model = new Plan(plan.getCode(), plan.getName(),
                BillingInterval.fromValue(plan.getInterval().name()), plan.getCurrency());
        model.setDescription(plan.getDescription());
        model.setAmountMinor(plan.getAmountMinor());
        model.setLimits(plan.getLimits().stream()
                .map(limit -> new PlanLimit(
                        UsageMetric.fromValue(limit.getMetric().name()), limit.getMaxQuantity()))
                .toList());
        return model;
    }

    public Subscription toModel(com.processpuzzle.platformadmin.domain.Subscription subscription) {
        Subscription model = new Subscription(
                subscription.getId(),
                subscription.getOrgKey(),
                subscription.getPlanCode(),
                SubscriptionStatus.fromValue(subscription.getStatus().name()),
                toOffsetDateTime(subscription.getCurrentPeriodStart()),
                toOffsetDateTime(subscription.getCurrentPeriodEnd()));
        model.setCanceledAt(toOffsetDateTime(subscription.getCanceledAt()));
        model.setCreatedAt(toOffsetDateTime(subscription.getCreatedAt()));
        model.setUpdatedAt(toOffsetDateTime(subscription.getUpdatedAt()));
        return model;
    }

    public UsageRecord toModel(com.processpuzzle.platformadmin.domain.UsageRecord usageRecord) {
        UsageRecord model = new UsageRecord(
                usageRecord.getId(),
                usageRecord.getOrgKey(),
                UsageMetric.fromValue(usageRecord.getMetric().name()),
                usageRecord.getQuantity(),
                toOffsetDateTime(usageRecord.getPeriodStart()),
                toOffsetDateTime(usageRecord.getPeriodEnd()));
        model.setRecordedAt(toOffsetDateTime(usageRecord.getRecordedAt()));
        return model;
    }

    public Invoice toModel(com.processpuzzle.platformadmin.domain.Invoice invoice) {
        Invoice model = new Invoice(
                invoice.getId(),
                invoice.getOrgKey(),
                InvoiceStatus.fromValue(invoice.getStatus().name()),
                invoice.getCurrency(),
                invoice.getTotalMinor(),
                toOffsetDateTime(invoice.getPeriodStart()),
                toOffsetDateTime(invoice.getPeriodEnd()));
        model.setNumber(invoice.getNumber());
        model.setIssuedAt(toOffsetDateTime(invoice.getIssuedAt()));
        model.setPaidAt(toOffsetDateTime(invoice.getPaidAt()));
        model.setLines(invoice.getLines().stream().map(PlatformAdminMapper::toModel).toList());
        return model;
    }

    /**
     * {@code metric} is set only when present: a flat plan fee has no metric, and
     * {@code UsageMetric.fromValue(null)} would throw rather than yield null.
     */
    private static InvoiceLine toModel(com.processpuzzle.platformadmin.domain.InvoiceLine line) {
        InvoiceLine model = new InvoiceLine(line.getDescription(), line.getQuantity(),
                line.getUnitAmountMinor(), line.getAmountMinor());
        if (line.getMetric() != null) {
            model.setMetric(UsageMetric.fromValue(line.getMetric().name()));
        }
        return model;
    }

    /**
     * Subscription and plan are set only when present. A tenant with no subscription is a normal
     * state — nothing creates subscriptions yet — and a subscription can name a plan withdrawn from
     * the catalog, so both halves are independently optional.
     */
    public OrganizationBilling toModel(GetOrganizationBilling.Result result) {
        OrganizationBilling model = new OrganizationBilling(result.orgKey());
        if (result.subscription() != null) {
            model.setSubscription(toModel(result.subscription()));
        }
        if (result.plan() != null) {
            model.setPlan(toModel(result.plan()));
        }
        model.setUsage(result.usage().stream().map(this::toModel).toList());
        model.setInvoices(result.invoices().stream().map(this::toModel).toList());
        return model;
    }

    public PageOfSubscription toSubscriptionPage(
            Page<com.processpuzzle.platformadmin.domain.Subscription> page) {
        PageOfSubscription model = new PageOfSubscription();
        model.setContent(page.getContent().stream().map(this::toModel).toList());
        model.setTotalElements(page.getTotalElements());
        model.setTotalPages(page.getTotalPages());
        model.setNumber(page.getNumber());
        model.setSize(page.getSize());
        return model;
    }

    public PageOfInvoice toInvoicePage(Page<com.processpuzzle.platformadmin.domain.Invoice> page) {
        PageOfInvoice model = new PageOfInvoice();
        model.setContent(page.getContent().stream().map(this::toModel).toList());
        model.setTotalElements(page.getTotalElements());
        model.setTotalPages(page.getTotalPages());
        model.setNumber(page.getNumber());
        model.setSize(page.getSize());
        return model;
    }

    public List<Plan> toPlanList(List<com.processpuzzle.platformadmin.domain.Plan> plans) {
        return plans.stream().map(this::toModel).toList();
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
