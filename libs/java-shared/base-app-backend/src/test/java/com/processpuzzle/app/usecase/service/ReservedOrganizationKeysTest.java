package com.processpuzzle.app.usecase.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An organization key becomes a top-level path segment of the public site, so it shares a namespace
 * with the platform's own URLs — a tenant called {@code api} would shadow the REST API.
 */
class ReservedOrganizationKeysTest {

    @Test
    void thePlatformsOwnTopLevelRoutesAreReserved() {
        ReservedOrganizationKeys keys = new ReservedOrganizationKeys(List.of());

        assertThat(keys.isReserved("api")).isTrue();
        assertThat(keys.isReserved("admin")).isTrue();
        assertThat(keys.isReserved("well-known")).isTrue();
        assertThat(keys.isReserved("my-org")).isFalse();
    }

    /** The property is read from yaml, so it is absent rather than empty on most deployments. */
    @Test
    void aDeploymentThatConfiguresNothingStillGetsTheDefaults() {
        assertThat(new ReservedOrganizationKeys(null).isReserved("api")).isTrue();
    }

    @Test
    void additionallyReservedKeysAreNormalisedBeforeBeingAdded() {
        ReservedOrganizationKeys keys = new ReservedOrganizationKeys(List.of("Acme", "  demo  "));

        assertThat(keys.isReserved("acme")).isTrue();
        assertThat(keys.isReserved("demo")).isTrue();
        assertThat(keys.all()).contains("acme", "demo", "api");
    }

    /** A trailing comma in the yaml list yields a blank entry; a missing value yields a null one. */
    @Test
    void blankAndAbsentAdditionalEntriesAreIgnored() {
        ReservedOrganizationKeys keys =
                new ReservedOrganizationKeys(Arrays.asList("acme", "", "   ", null));

        assertThat(keys.isReserved("acme")).isTrue();
        assertThat(keys.all()).doesNotContain("", "   ").hasSize(22);
    }

    @Test
    void theCandidateIsNormalisedBeforeBeingComparedAndAnAbsentOneIsNotReserved() {
        ReservedOrganizationKeys keys = new ReservedOrganizationKeys(List.of());

        assertThat(keys.isReserved("  API  ")).isTrue();
        assertThat(keys.isReserved(null)).isFalse();
    }

    @Test
    void theReservedSetCannotBeExtendedByACaller() {
        assertThatThrownBy(() -> new ReservedOrganizationKeys(List.of()).all().add("my-org"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
