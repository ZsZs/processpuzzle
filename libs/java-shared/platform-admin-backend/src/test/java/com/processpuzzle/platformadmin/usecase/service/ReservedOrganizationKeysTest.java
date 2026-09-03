package com.processpuzzle.platformadmin.usecase.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An organization key becomes a top-level path segment of the public site, so it shares a namespace
 * with the platform's own URLs — a tenant called {@code api} would shadow the REST API.
 */
@SuppressWarnings("java:S5778")
class ReservedOrganizationKeysTest {

    @Test
    void thePlatformsOwnTopLevelRoutesAreReserved() {
        ReservedOrganizationKeys keys = new ReservedOrganizationKeys(List.of(), null);

        assertThat(keys.isReserved("api")).isTrue();
        assertThat(keys.isReserved("admin")).isTrue();
        assertThat(keys.isReserved("well-known")).isTrue();
        assertThat(keys.isReserved("my-org")).isFalse();
    }

    /**
     * The platform stacks own their organization keys in every deployment, so these are defaults and
     * not a local policy choice. For the testbed and admin stacks the organization key, the Keycloak
     * realm and the MinIO bucket prefix are one string, which is why letting a customer claim either
     * one would hand them a realm and a bucket namespace as well as a URL segment.
     */
    @Test
    void theApplicationStacksOwnOrganizationKeysAreReserved() {
        ReservedOrganizationKeys keys = new ReservedOrganizationKeys(List.of(), null);

        assertThat(keys.isReserved("processpuzzle-testbed")).isTrue();
        assertThat(keys.isReserved("processpuzzle-admin")).isTrue();
    }

    /**
     * The regression this guards against: reserving the key a deployment serves stops the deployment
     * from creating its own organization. {@code DefaultAppLoader} bootstraps it through the ordinary
     * claim path and skips a key it cannot claim, so the testbed came up with no organization at all
     * and every {@code createAppDefinition} in it answered {@code OrganizationNotFoundException}.
     */
    @Test
    void theKeyOfTheStackThisDeploymentServesIsClaimableSoItCanSeedItsOwnOrganization() {
        ReservedOrganizationKeys keys =
                new ReservedOrganizationKeys(List.of(), "processpuzzle-testbed");

        assertThat(keys.isReserved("processpuzzle-testbed")).isFalse();
        assertThat(keys.all()).doesNotContain("processpuzzle-testbed");
    }

    /** Only its own: a stack must not be able to claim a sibling stack's realm and bucket namespace. */
    @Test
    void everyOtherStacksKeyStaysReservedOnThatSameDeployment() {
        ReservedOrganizationKeys keys =
                new ReservedOrganizationKeys(List.of(), "processpuzzle-testbed");

        assertThat(keys.isReserved("processpuzzle-admin")).isTrue();
        assertThat(keys.isReserved("api")).isTrue();
    }

    /** The property is a realm name, so it arrives however Keycloak spells it. */
    @Test
    void theOwnStackKeyIsNormalisedBeforeBeingExempted() {
        ReservedOrganizationKeys keys =
                new ReservedOrganizationKeys(List.of(), "  ProcessPuzzle-Testbed  ");

        assertThat(keys.isReserved("processpuzzle-testbed")).isFalse();
    }

    /**
     * Applied after the configured additions, so a deployment cannot reserve the key it serves. Doing
     * so would only disable its own bootstrap, which no deployment can mean.
     */
    @Test
    void aDeploymentCannotReserveTheKeyItServesEvenByConfiguringItExplicitly() {
        ReservedOrganizationKeys keys = new ReservedOrganizationKeys(
                List.of("processpuzzle-testbed", "acme"), "processpuzzle-testbed");

        assertThat(keys.isReserved("processpuzzle-testbed")).isFalse();
        assertThat(keys.isReserved("acme")).isTrue();
    }

    /** A deployment that serves no stack — a library test, or a plain unconfigured boot. */
    @Test
    void anUnsetOwnStackKeyLeavesBothStackKeysReserved() {
        assertThat(new ReservedOrganizationKeys(List.of(), "   ").isReserved("processpuzzle-admin"))
                .isTrue();
        assertThat(new ReservedOrganizationKeys(List.of(), null).isReserved("processpuzzle-testbed"))
                .isTrue();
    }

    /** The property is read from yaml, so it is absent rather than empty on most deployments. */
    @Test
    void aDeploymentThatConfiguresNothingStillGetsTheDefaults() {
        assertThat(new ReservedOrganizationKeys(null, null).isReserved("api")).isTrue();
    }

    @Test
    void additionallyReservedKeysAreNormalisedBeforeBeingAdded() {
        ReservedOrganizationKeys keys = new ReservedOrganizationKeys(List.of("Acme", "  demo  "), null);

        assertThat(keys.isReserved("acme")).isTrue();
        assertThat(keys.isReserved("demo")).isTrue();
        assertThat(keys.all()).contains("acme", "demo", "api");
    }

    /** A trailing comma in the yaml list yields a blank entry; a missing value yields a null one. */
    @Test
    void blankAndAbsentAdditionalEntriesAreIgnored() {
        ReservedOrganizationKeys keys =
                new ReservedOrganizationKeys(Arrays.asList("acme", "", "   ", null), null);

        assertThat(keys.isReserved("acme")).isTrue();
        // Sized against a baseline rather than a literal: the default list gains an entry whenever the
        // platform claims a new top-level segment ("platform" was the last), and a magic number here
        // turns that into an unrelated test failure.
        int defaults = new ReservedOrganizationKeys(List.of(), null).all().size();
        assertThat(keys.all()).doesNotContain("", "   ").hasSize(defaults + 1);
    }

    @Test
    void theCandidateIsNormalisedBeforeBeingComparedAndAnAbsentOneIsNotReserved() {
        ReservedOrganizationKeys keys = new ReservedOrganizationKeys(List.of(), null);

        assertThat(keys.isReserved("  API  ")).isTrue();
        assertThat(keys.isReserved(null)).isFalse();
    }

    @Test
    void theReservedSetCannotBeExtendedByACaller() {
        assertThatThrownBy(() -> new ReservedOrganizationKeys(List.of(), null).all().add("my-org"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
