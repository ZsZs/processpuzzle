package com.processpuzzle.baseentity.unit;

import com.processpuzzle.baseentity.unit.domain.OrganizationUnit;
import com.processpuzzle.baseentity.unit.domain.OrganizationUnitRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizationUnitCatalogsTest {

    private final OrganizationUnitRepository repository = mock(OrganizationUnitRepository.class);
    private final OrganizationUnitCatalogs catalogs = new OrganizationUnitCatalogs(repository);

    private static OrganizationUnit unit(String kind, String code) {
        OrganizationUnit unit = new OrganizationUnit();
        unit.setCode(code);
        unit.setSymbol(code);
        unit.setName(code);
        unit.setKind(kind);
        unit.setFactor(new BigDecimal("500"));
        unit.setDivisor(BigDecimal.ONE);
        return unit;
    }

    @Test
    void noOrganizationMeansTheStandardCatalog() {
        assertThat(catalogs.forOrganization(null).findUnit("pallet")).isEmpty();
        assertThat(catalogs.forOrganization(null).findUnit("kg")).isPresent();
    }

    @Test
    void aCatalogIsBuiltOnceAndKeptUntilEvicted() {
        when(repository.findAllByOrgKeyOrderByCode("acme")).thenReturn(List.of(unit("MASS", "pallet")));

        assertThat(catalogs.forOrganization("acme").findUnit("pallet")).isPresent();
        catalogs.forOrganization("acme");
        verify(repository, times(1)).findAllByOrgKeyOrderByCode("acme");

        catalogs.evict("acme");
        catalogs.forOrganization("acme");
        verify(repository, times(2)).findAllByOrgKeyOrderByCode("acme");
    }

    @Test
    void aRowTheCatalogRefusesIsLeftOutAndTheOthersStay() {
        when(repository.findAllByOrgKeyOrderByCode("acme")).thenReturn(List.of(unit("NO_SUCH_KIND", "ghost"), unit("MASS", "pallet")));

        var catalog = catalogs.forOrganization("acme");

        assertThat(catalog.findUnit("ghost")).isEmpty();
        assertThat(catalog.findUnit("pallet")).isPresent();
    }

    @Test
    void insideATransactionTheCatalogIsDroppedOnlyOnceItCompletes() {
        when(repository.findAllByOrgKeyOrderByCode("acme")).thenReturn(List.of());
        catalogs.forOrganization("acme");
        TransactionSynchronizationManager.initSynchronization();
        try {
            catalogs.evict("acme");
            catalogs.forOrganization("acme");
            verify(repository, times(1)).findAllByOrgKeyOrderByCode("acme");

            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        catalogs.forOrganization("acme");
        verify(repository, times(2)).findAllByOrgKeyOrderByCode("acme");
    }
}
