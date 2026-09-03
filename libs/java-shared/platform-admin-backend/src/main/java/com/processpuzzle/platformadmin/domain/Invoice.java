package com.processpuzzle.platformadmin.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * What a tenant was charged for one period.
 *
 * <p>{@code number} is nullable and {@code id} is not: a {@code DRAFT} invoice has an identity but no
 * human-facing number, because numbers must be gapless once issued and handing one out to a draft
 * that is later discarded would leave a hole an auditor asks about.
 *
 * <p>Lines are an {@code @ElementCollection} loaded eagerly. An invoice is never useful without them
 * — every read of one renders the lines — so lazy loading here would buy nothing but an
 * {@code N+1} on the list endpoint.
 */
@Entity
@Table(name = "billing_invoices", indexes = @Index(name = "ix_invoice_org", columnList = "org_key"))
public class Invoice {

    @Id
    @Column(length = 63)
    private String id;

    @Column(name = "org_key", nullable = false, length = 63)
    private String orgKey;

    /** Human-facing, gapless once issued. Null while {@code DRAFT}. */
    @Column(name = "invoice_number", length = 63)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "billing_invoice_lines", joinColumns = @JoinColumn(name = "invoice_id"))
    private List<InvoiceLine> lines = new ArrayList<>();

    protected Invoice() {
        // required by JPA
    }

    public Invoice(String id, String orgKey, Details details) {
        this.id = id;
        this.orgKey = orgKey;
        this.number = details.number();
        this.status = details.status() == null ? InvoiceStatus.DRAFT : details.status();
        this.currency = details.currency() == null ? "EUR" : details.currency();
        this.totalMinor = details.totalMinor();
        this.periodStart = details.periodStart();
        this.periodEnd = details.periodEnd();
        this.lines = details.lines() == null ? new ArrayList<>() : new ArrayList<>(details.lines());
    }

    public record Details(String number, InvoiceStatus status, String currency, long totalMinor,
                          Instant periodStart, Instant periodEnd, List<InvoiceLine> lines) {}

    public String getId() {
        return id;
    }

    public String getOrgKey() {
        return orgKey;
    }

    public String getNumber() {
        return number;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public long getTotalMinor() {
        return totalMinor;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public List<InvoiceLine> getLines() {
        return List.copyOf(lines);
    }
}
