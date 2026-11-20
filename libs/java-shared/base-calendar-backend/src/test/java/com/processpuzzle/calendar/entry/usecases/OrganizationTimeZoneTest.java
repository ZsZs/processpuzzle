package com.processpuzzle.calendar.entry.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.calendar.entry.usecases.inbound.OrganizationTimeZone;
import com.processpuzzle.calendar.entry.usecases.outbound.OrganizationTimeZonePort;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class OrganizationTimeZoneTest {

    private static OrganizationTimeZone zone(OrganizationTimeZonePort port, String configured) {
        @SuppressWarnings("unchecked")
        ObjectProvider<OrganizationTimeZonePort> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> port != null ? port : ((java.util.function.Supplier<?>) call.getArgument(0)).get());
        return new OrganizationTimeZone(provider, configured);
    }

    @Test
    void theOrganizationsOwnSettingWins() {
        OrganizationTimeZone zone = zone(orgKey -> Optional.of("Europe/Budapest"), "UTC");

        assertThat(zone.of("acme")).isEqualTo(ZoneId.of("Europe/Budapest"));
    }

    @Test
    void anOrganizationWithoutASettingGetsTheConfiguredDefault() {
        OrganizationTimeZone zone = zone(orgKey -> Optional.empty(), "America/New_York");

        assertThat(zone.of("acme")).isEqualTo(ZoneId.of("America/New_York"));
    }

    @Test
    void aHostWithoutAnAdapterGetsTheConfiguredDefault() {
        assertThat(zone(null, "Asia/Tokyo").of("acme")).isEqualTo(ZoneId.of("Asia/Tokyo"));
    }

    @Test
    void theDefaultIsUtcWhenNothingIsConfigured() {
        assertThat(zone(null, "UTC").of("acme")).isEqualTo(ZoneId.of("UTC"));
    }

    @Test
    void aZoneTheHostReturnsThatJavaDoesNotKnowIsNoSetting() {
        OrganizationTimeZone zone = zone(orgKey -> Optional.of("Mars/Olympus_Mons"), "Europe/Berlin");

        assertThat(zone.of("acme")).isEqualTo(ZoneId.of("Europe/Berlin"));
    }

    @Test
    void anEmptyZoneIdFromTheHostIsNoSetting() {
        OrganizationTimeZone zone = zone(orgKey -> Optional.of(""), "Europe/Berlin");

        assertThat(zone.of("acme")).isEqualTo(ZoneId.of("Europe/Berlin"));
    }

    @Test
    void aMistypedConfiguredDefaultStopsTheStartUp() {
        assertThatThrownBy(() -> zone(null, "Nowhere/Land"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base-calendar.default-time-zone")
                .hasMessageContaining("Nowhere/Land");
    }
}
