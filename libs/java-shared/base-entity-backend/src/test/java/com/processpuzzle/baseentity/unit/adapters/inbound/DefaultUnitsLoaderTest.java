package com.processpuzzle.baseentity.unit.adapters.inbound;

import com.processpuzzle.baseentity.exchange.OrganizationExchangeRates;
import com.processpuzzle.baseentity.exchange.domain.OrganizationExchangeRate;
import com.processpuzzle.baseentity.exchange.domain.OrganizationExchangeRateRepository;
import com.processpuzzle.baseentity.unit.OrganizationUnitCatalogs;
import com.processpuzzle.baseentity.unit.domain.OrganizationUnit;
import com.processpuzzle.baseentity.unit.domain.OrganizationUnitRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultUnitsLoaderTest {

    private static final String YAML = """
            units:
              - { code: pallet, symbol: plt, name: Pallet, kind: MASS, factor: '500' }
              - { code: half, symbol: hf, name: Half, kind: MASS, factor: '1', divisor: '2' }
              - { code: nokind, symbol: nk, name: No kind, kind: SPEED_OF_LIGHT, factor: '1' }
              - { code: kg, symbol: kg, name: Kilogram again, kind: MASS, factor: '1' }
              - { code: heavy, symbol: hv, name: Heavy, kind: MASS, factor: 'a lot' }
              - { symbol: nc, name: No code, kind: MASS, factor: '1' }
            exchangeRates:
              - { from: EUR, to: USD, rate: '1.08', validFrom: '2026-01-01' }
              - { from: EUR, to: EUR, rate: '1', validFrom: '2026-01-01' }
              - { from: EUR, to: HUF, rate: '395.5', validFrom: 'yesterday' }
              - { from: EUR, to: HUF, rate: '0', validFrom: '2026-01-01' }
            """;

    private OrganizationUnitRepository units;
    private OrganizationExchangeRateRepository rates;
    private OrganizationUnitCatalogs catalogs;
    private OrganizationExchangeRates tables;
    private ResourcePatternResolver resolver;
    private DefaultUnitsLoader loader;

    @BeforeEach
    void setUp() {
        units = mock(OrganizationUnitRepository.class);
        rates = mock(OrganizationExchangeRateRepository.class);
        catalogs = mock(OrganizationUnitCatalogs.class);
        tables = mock(OrganizationExchangeRates.class);
        resolver = mock(ResourcePatternResolver.class);
        loader = new DefaultUnitsLoader(units, rates, catalogs, tables, resolver);
        when(rates.findByOrgKeyAndFromCurrencyAndToCurrencyAndValidFrom(anyString(), anyString(), anyString(), any())).thenReturn(Optional.empty());
    }

    private static Resource file(String name, String yaml) {
        return new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return name;
            }
        };
    }

    @Test
    void seedsTheValidUnitsAndRatesOfTheOrganizationNamedInTheFile() throws IOException {
        when(resolver.getResources(anyString())).thenReturn(new Resource[] {file("acme-units.yaml", YAML)});

        loader.loadDefaults();

        ArgumentCaptor<OrganizationUnit> unit = ArgumentCaptor.forClass(OrganizationUnit.class);
        verify(units, times(2)).save(unit.capture());
        assertThat(unit.getAllValues()).extracting(OrganizationUnit::getCode).containsExactly("pallet", "half");
        assertThat(unit.getAllValues()).allSatisfy(saved -> assertThat(saved.getOrgKey()).isEqualTo("acme"));
        assertThat(unit.getAllValues().get(0).getDivisor()).isEqualByComparingTo("1");
        assertThat(unit.getAllValues().get(1).getDivisor()).isEqualByComparingTo("2");

        ArgumentCaptor<OrganizationExchangeRate> rate = ArgumentCaptor.forClass(OrganizationExchangeRate.class);
        verify(rates, times(1)).save(rate.capture());
        assertThat(rate.getValue().getFromCurrency()).isEqualTo("EUR");
        assertThat(rate.getValue().getToCurrency()).isEqualTo("USD");
        assertThat(rate.getValue().getRate()).isEqualByComparingTo("1.08");
        assertThat(rate.getValue().getValidFrom()).isEqualTo(LocalDate.parse("2026-01-01"));
        verify(catalogs).evict("acme");
        verify(tables).evict("acme");
    }

    @Test
    void leavesWhatTheOrganizationAlreadyHasAsItIs() throws IOException {
        when(resolver.getResources(anyString())).thenReturn(new Resource[] {file("acme-units.yaml", YAML)});
        when(units.existsByOrgKeyAndCode("acme", "pallet")).thenReturn(true);
        when(rates.findByOrgKeyAndFromCurrencyAndToCurrencyAndValidFrom("acme", "EUR", "USD", LocalDate.parse("2026-01-01")))
            .thenReturn(Optional.of(new OrganizationExchangeRate()));

        loader.loadDefaults();

        ArgumentCaptor<OrganizationUnit> unit = ArgumentCaptor.forClass(OrganizationUnit.class);
        verify(units, times(1)).save(unit.capture());
        assertThat(unit.getValue().getCode()).isEqualTo("half");
        verify(rates, never()).save(any());
    }

    @Test
    void skipsAFileWhoseNameNamesNoOrganizationOrWhichCannotBeRead() throws IOException {
        when(resolver.getResources(anyString())).thenReturn(new Resource[] {file("-units.yaml", YAML), file("units.txt", YAML), file("broken-units.yaml", "units: [")});

        loader.loadDefaults();

        verify(units, never()).save(any());
        verify(rates, never()).save(any());
    }

    @Test
    void aFailedSaveIsLoggedAndTheRestStillLoad() throws IOException {
        when(resolver.getResources(anyString())).thenReturn(new Resource[] {file("acme-units.yaml", YAML)});
        when(units.save(any(OrganizationUnit.class))).thenThrow(new IllegalStateException("database says no")).thenAnswer(call -> call.getArgument(0));

        loader.loadDefaults();

        verify(units, times(2)).save(any(OrganizationUnit.class));
        verify(rates, times(1)).save(any(OrganizationExchangeRate.class));
    }

    @Test
    void anUnreadableLocationLoadsNothing() throws IOException {
        when(resolver.getResources(anyString())).thenThrow(new IOException("no such place"));

        loader.loadDefaults();

        verify(units, never()).save(any());
    }

    @Test
    void aFileWithOnlyUnitsOrOnlyRatesLoads() throws IOException {
        when(resolver.getResources(anyString())).thenReturn(new Resource[] {file("only-units.yaml", "units:\n  - { code: tote, symbol: tt, name: Tote, kind: MASS, factor: '10' }\n")});

        loader.loadDefaults();

        verify(units, times(1)).save(any(OrganizationUnit.class));
        verify(rates, never()).save(any());
    }
}
