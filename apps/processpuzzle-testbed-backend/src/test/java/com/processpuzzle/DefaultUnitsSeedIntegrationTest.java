package com.processpuzzle;

import com.processpuzzle.baseentity.exchange.OrganizationExchangeRates;
import com.processpuzzle.baseentity.exchange.domain.OrganizationExchangeRateRepository;
import com.processpuzzle.baseentity.unit.OrganizationUnitCatalogs;
import com.processpuzzle.baseentity.unit.adapters.inbound.DefaultUnitsLoader;
import com.processpuzzle.baseentity.unit.domain.OrganizationUnitRepository;
import com.processpuzzle.core.quantity.Money;
import com.processpuzzle.core.quantity.Quantity;
import com.processpuzzle.core.quantity.UnitCatalog;
import java.io.IOException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The testbed's seed file {@code default-units/processpuzzle-testbed-units.yaml}, through the real repositories and providers: its
 * units are the organization's, its rates convert, and a second load changes nothing. Loaded into an organization of its own, the
 * file renamed — the seed's own organization belongs to the other tests.
 */
@SpringBootTest
class DefaultUnitsSeedIntegrationTest {

    private static final String ORG = "seed-check";
    private static final LocalDate MARCH = LocalDate.parse("2026-03-01");
    private static final LocalDate AUGUST = LocalDate.parse("2026-08-01");

    @Autowired private OrganizationUnitRepository units;
    @Autowired private OrganizationExchangeRateRepository rates;
    @Autowired private OrganizationUnitCatalogs catalogs;
    @Autowired private OrganizationExchangeRates tables;

    private DefaultUnitsLoader loader() throws IOException {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        Resource seed = new ClassPathResource("default-units/processpuzzle-testbed-units.yaml");
        Resource renamed = new org.springframework.core.io.ByteArrayResource(seed.getContentAsByteArray()) {
            @Override
            public String getFilename() {
                return ORG + "-units.yaml";
            }
        };
        when(resolver.getResources(anyString())).thenReturn(new Resource[] {renamed});
        return new DefaultUnitsLoader(units, rates, catalogs, tables, resolver);
    }

    @Test
    void theSeedGivesTheOrganizationItsUnitsAndRatesAndLoadingAgainChangesNothing() throws IOException {
        loader().loadDefaults();

        UnitCatalog catalog = catalogs.forOrganization(ORG);
        assertThat(catalog.findUnit("pallet")).isPresent();
        assertThat(Quantity.of("2", "pallet").baseAmount(catalog)).isEqualByComparingTo("1000");
        assertThat(Quantity.of("2", "carton").baseAmount(catalog)).isEqualByComparingTo("12");
        assertThat(catalogs.forOrganization("someone-else").findUnit("pallet")).isEmpty();

        var table = tables.forOrganization(ORG);
        assertThat(Money.of("100", "EUR").convertTo("USD", table, MARCH, catalog)).isEqualTo(Money.of("108", "USD"));
        assertThat(Money.of("100", "EUR").convertTo("USD", table, AUGUST, catalog)).isEqualTo(Money.of("112", "USD"));
        assertThat(Money.of("108", "USD").convertTo("EUR", table, MARCH, catalog)).isEqualTo(Money.of("100", "EUR"));
        assertThat(Money.of("100", "USD").convertTo("HUF", table, MARCH, catalog).currency()).as("a cross rate through EUR").isEqualTo("HUF");

        long unitCount = units.findAllByOrgKeyOrderByCode(ORG).size();
        long rateCount = rates.findAllByOrgKey(ORG).size();
        loader().loadDefaults();
        assertThat(units.findAllByOrgKeyOrderByCode(ORG)).hasSize((int) unitCount);
        assertThat(rates.findAllByOrgKey(ORG)).hasSize((int) rateCount);
        assertThat(unitCount).isEqualTo(3);
        assertThat(rateCount).isEqualTo(3);
    }
}
