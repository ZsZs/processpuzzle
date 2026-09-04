package com.processpuzzle.platformadmin.adapter.inbound.dto;

/**
 * Root of a bundled {@code default-organizations/<orgKey>-organization.yaml} file, read by
 * {@link com.processpuzzle.platformadmin.adapter.inbound.DefaultOrganizationLoader} on startup.
 *
 * <p>The tenant used to be provisioned by base-app's {@code default-apps/<orgKey>-apps.yaml}, whose
 * loader called {@code provisionOrganization} when the key was still free. That put the creation of
 * a tenant in the hands of a feature that merely stores data scoped by one, and was a large part of
 * why base-app depended on platform-admin at all. Every other feature's loader already assumed the
 * tenant existed; now base-app's does too, and the tenant is seeded by the module that owns it.
 *
 * <p>{@code key} is informational. The owning organization is the part of the file name before
 * {@code -organization.yaml}, matching {@code <orgKey>-apps.yaml}, {@code <orgKey>-rules.yaml} and
 * the rest, so a file copied between deployments cannot silently seed the tenant it came from.
 *
 * @param key the tenant's key as documentation; the file name decides
 * @param name display name; the key is used when blank
 * @param description free text
 * @param contactEmail operational contact
 * @param defaultLocale BCP-47 tag the tenant's shell activates
 */
public record DefaultOrganizationDocument(String key,
                                          String name,
                                          String description,
                                          String contactEmail,
                                          String defaultLocale) {
}
