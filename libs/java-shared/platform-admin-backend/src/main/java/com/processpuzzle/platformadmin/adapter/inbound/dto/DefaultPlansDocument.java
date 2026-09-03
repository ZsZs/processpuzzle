package com.processpuzzle.platformadmin.adapter.inbound.dto;

import java.util.List;
import java.util.Map;

/**
 * The shape of a {@code default-plans/*-plans.yaml} file.
 *
 * <p>A record of plain types rather than the {@code Plan} entity, so a malformed file is a
 * deserialization failure with a readable message instead of a half-built entity. Limits arrive as a
 * {@code metric -> maxQuantity} map because that is what reads naturally in YAML; the loader converts
 * to the list the domain stores.
 *
 * @param plans the plan catalog declared by this file
 */
public record DefaultPlansDocument(List<PlanEntry> plans) {

    /**
     * @param code stable identifier a subscription names; also the primary key
     * @param interval {@code MONTHLY} or {@code YEARLY}; defaults to monthly when absent
     * @param currency ISO-4217; defaults to {@code EUR} when absent
     * @param amountMinor price per interval in the currency's minor unit — cents, never a decimal
     * @param limits per-metric ceilings; an absent metric means unmetered, a {@code 0} means unavailable
     */
    public record PlanEntry(String code, String name, String description, String interval,
                            String currency, Long amountMinor, Map<String, Long> limits) {
    }
}
