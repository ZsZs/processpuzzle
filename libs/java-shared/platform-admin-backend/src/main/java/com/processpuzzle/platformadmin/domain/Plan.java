package com.processpuzzle.platformadmin.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * A purchasable entitlement level. Keyed by a stable {@code code} ({@code free}, {@code team},
 * {@code enterprise}) rather than a generated id, because a {@link Subscription} names it and a
 * seeded catalog has to be re-runnable without minting duplicates.
 *
 * <p><b>Money is a {@code long} of minor units, never a floating-point type.</b> A price in a binary
 * fraction accumulates error the first time it is summed across invoice lines, and the error is in
 * the currency the customer is charged in. {@code amountMinor} is cents (or the currency's
 * equivalent), and {@code currency} says which currency's.
 */
@Entity
@Table(name = "billing_plans")
public class Plan {

    @Id
    @Column(length = 63)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 20)
    private BillingInterval interval = BillingInterval.MONTHLY;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    /** Price per interval in the currency's minor unit. See the class comment. */
    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "billing_plan_limits", joinColumns = @JoinColumn(name = "plan_code"))
    private List<PlanLimit> limits = new ArrayList<>();

    protected Plan() {
        // required by JPA
    }

    public Plan(String code, String name, String description, BillingInterval interval,
                String currency, long amountMinor, List<PlanLimit> limits) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.interval = interval == null ? BillingInterval.MONTHLY : interval;
        this.currency = currency == null ? "EUR" : currency;
        this.amountMinor = amountMinor;
        this.limits = limits == null ? new ArrayList<>() : new ArrayList<>(limits);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BillingInterval getInterval() {
        return interval;
    }

    public String getCurrency() {
        return currency;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public List<PlanLimit> getLimits() {
        return List.copyOf(limits);
    }
}
