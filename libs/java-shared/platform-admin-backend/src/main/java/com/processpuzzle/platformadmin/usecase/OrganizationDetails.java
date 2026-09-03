package com.processpuzzle.platformadmin.usecase;

/**
 * The descriptive, mutable half of an organization — everything except the key.
 *
 * <p>A use-case-level record rather than either contract's generated DTO, and that is the point of
 * it. base-app's {@code OrganizationInput} / {@code OrganizationUpdate} live in
 * {@code com.processpuzzle.app.model}, which belongs to the {@code app} Modulith module; taking one
 * as a parameter here would make {@code platformadmin} depend on {@code app}, which depends on
 * {@code platformadmin} — a cycle. Taking platform-admin's own {@code OrganizationUpdate} instead
 * would work but would make the tenant-facing caller convert into a staff-facing DTO for no reason.
 *
 * <p>So both adapters map into this, and neither use case knows which contract its caller speaks.
 * The key is deliberately not a field: it is the identity of the aggregate, immutable once claimed,
 * and passed separately so that no update path can appear to accept a new one.
 */
public record OrganizationDetails(String name, String description, String contactEmail, String defaultLocale) {
}
