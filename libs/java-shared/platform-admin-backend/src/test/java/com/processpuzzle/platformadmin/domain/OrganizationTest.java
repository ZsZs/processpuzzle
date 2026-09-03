package com.processpuzzle.platformadmin.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tenant entity. Its key is immutable by design — it is the public URL segment and the scope of
 * every piece of metadata belonging to the tenant, so there is deliberately no setter for it.
 */
class OrganizationTest {

    @Test
    void carriesTheDescriptiveFieldsItWasProvisionedWith() {
        Organization organization = new Organization("my-org", "My Organization Ltd.", "Insurance.",
                "ops@my-org.example", "en-GB", OrganizationStatus.PROVISIONING);

        assertThat(organization.getKey()).isEqualTo("my-org");
        assertThat(organization.getName()).isEqualTo("My Organization Ltd.");
        assertThat(organization.getDescription()).isEqualTo("Insurance.");
        assertThat(organization.getContactEmail()).isEqualTo("ops@my-org.example");
        assertThat(organization.getDefaultLocale()).isEqualTo("en-GB");
        assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.PROVISIONING);
    }

    /** {@code status} is a non-nullable column, so an unstated status has to mean something. */
    @Test
    void anUnstatedStatusDefaultsToActive() {
        Organization organization = new Organization("my-org", "My Organization Ltd.", null, null, null, null);

        assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
    }

    @Test
    void everyDescriptiveFieldIsReplaceableWhileTheKeyIsNot() {
        Organization organization = new Organization("my-org", "My Organization Ltd.", null, null, null,
                OrganizationStatus.ACTIVE);

        organization.setName("My Organization GmbH");
        organization.setDescription("Now German.");
        organization.setContactEmail("betrieb@my-org.example");
        organization.setDefaultLocale("de-DE");
        organization.setStatus(OrganizationStatus.SUSPENDED);

        assertThat(organization.getName()).isEqualTo("My Organization GmbH");
        assertThat(organization.getDescription()).isEqualTo("Now German.");
        assertThat(organization.getContactEmail()).isEqualTo("betrieb@my-org.example");
        assertThat(organization.getDefaultLocale()).isEqualTo("de-DE");
        assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.SUSPENDED);
        assertThat(organization.getKey()).isEqualTo("my-org");
    }

    /** Both timestamp columns are non-nullable, so the insert callback has to set both. */
    @Test
    void bothTimestampsAreStampedOnInsertAndOnlyOneIsMovedOnUpdate() {
        Organization organization = new Organization("my-org", "My Organization Ltd.", null, null, null, null);

        assertThat(organization.getCreatedAt()).isNull();
        assertThat(organization.getUpdatedAt()).isNull();

        organization.onCreate();

        assertThat(organization.getCreatedAt()).isNotNull().isEqualTo(organization.getUpdatedAt());

        java.time.Instant created = organization.getCreatedAt();
        organization.onUpdate();

        assertThat(organization.getCreatedAt()).isEqualTo(created);
        assertThat(organization.getUpdatedAt()).isAfterOrEqualTo(created);
    }
}
