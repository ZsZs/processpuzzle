package com.processpuzzle.shared.event;

/**
 * A tenant's row has been committed as {@code PROVISIONING}, and its identity realm now needs to be
 * created.
 *
 * <p>Published by {@code ProvisionOrganization}, consumed by {@code OrganizationRealmProvisioner}
 * with {@code @TransactionalEventListener(phase = AFTER_COMMIT)} — after commit precisely because
 * creating a realm is a network call that must not hold a database transaction open, and because a
 * realm created for a transaction that then rolls back would be an orphan nothing knows to clean up.
 *
 * <p>The listener needs {@code @Transactional(REQUIRES_NEW)} to write the resulting {@code ACTIVE}
 * status: after-commit work runs with the original transaction already completed, so a write on it
 * is discarded without an error — and the misleading part is that the response to the creating
 * request still shows whatever the handler set in memory, so the loss only surfaces on the next read.
 *
 * @param orgKey the tenant whose realm is to be created; also the realm's name
 * @param organizationName display name, used as the realm's display name
 * @param defaultLocale BCP-47 tag, used as the realm's default locale; may be {@code null}
 */
public record OrganizationProvisionedEvent(String orgKey, String organizationName, String defaultLocale) {
}
