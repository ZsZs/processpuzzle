package com.processpuzzle.baseentity.unit;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.unit.domain.OrganizationUnit;
import com.processpuzzle.baseentity.unit.domain.OrganizationUnitRepository;
import com.processpuzzle.baseentity.unit.domain.UnitStatus;
import com.processpuzzle.baseentity.unit.usecases.inbound.ManageOrganizationUnits;
import com.processpuzzle.core.quantity.Quantity;
import com.processpuzzle.core.quantity.UnitCatalog;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManageOrganizationUnitsTest {

    private static final String ORG = "acme";

    private OrganizationUnitRepository repository;
    private OrganizationGuard guard;
    private OrganizationUnitCatalogs catalogs;
    private ManageOrganizationUnits units;

    @BeforeEach
    void setUp() {
        repository = mock(OrganizationUnitRepository.class);
        guard = mock(OrganizationGuard.class);
        catalogs = new OrganizationUnitCatalogs(repository);
        units = new ManageOrganizationUnits(repository, catalogs, guard);
        when(repository.save(ArgumentMatchers.any(OrganizationUnit.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static OrganizationUnit pallet() {
        OrganizationUnit unit = new OrganizationUnit();
        unit.setCode("pallet");
        unit.setSymbol("plt");
        unit.setName("Pallet");
        unit.setKind("MASS");
        unit.setFactor(new BigDecimal("500"));
        unit.setDivisor(BigDecimal.ONE);
        return unit;
    }

    private static OrganizationUnit stored() {
        OrganizationUnit unit = pallet();
        unit.setOrgKey(ORG);
        unit.setVersion(3L);
        return unit;
    }

    @Test
    void addsAUnitToTheOrganizationAndNeedsTheRightToDesign() {
        OrganizationUnit created = units.create(ORG, pallet());

        assertThat(created.getOrgKey()).isEqualTo(ORG);
        assertThat(created.getStatus()).isEqualTo(UnitStatus.ACTIVE);
        verify(guard).requireDesign(ORG);
    }

    @Test
    void theNewUnitIsInTheCatalogAfterTheCatalogWasBuiltWithoutIt() {
        assertThat(catalogs.forOrganization(ORG).findUnit("pallet")).isEmpty();
        when(repository.findAllByOrgKeyOrderByCode(ORG)).thenReturn(List.of(stored()));

        units.create(ORG, pallet());

        UnitCatalog catalog = catalogs.forOrganization(ORG);
        assertThat(Quantity.of("2", "pallet").baseAmount(catalog)).isEqualByComparingTo("1000");
        assertThat(catalogs.forOrganization("other").findUnit("pallet")).as("another organization does not get it").isEmpty();
    }

    @Test
    void refusesWhatIsNotAUnitOfAnExistingKind() {
        OrganizationUnit noFactor = pallet();
        noFactor.setFactor(null);
        OrganizationUnit negative = pallet();
        negative.setFactor(new BigDecimal("-1"));
        OrganizationUnit badKind = pallet();
        badKind.setKind("WEALTH");
        OrganizationUnit badCode = pallet();
        badCode.setCode("1 pallet");

        for (OrganizationUnit unit : List.of(noFactor, negative, badKind, badCode)) {
            assertThatThrownBy(() -> units.create(ORG, unit)).isInstanceOf(ValidationException.class);
        }
        verify(repository, never()).save(ArgumentMatchers.any());
    }

    @Test
    void refusesACodeOfTheStandardCatalogAndOneTheOrganizationHas() {
        OrganizationUnit standard = pallet();
        standard.setCode("kg");
        assertThatThrownBy(() -> units.create(ORG, standard)).isInstanceOf(ConflictException.class).hasMessageContaining("standard catalog");

        when(repository.existsByOrgKeyAndCode(ORG, "pallet")).thenReturn(true);
        assertThatThrownBy(() -> units.create(ORG, pallet())).isInstanceOf(ConflictException.class).hasMessageContaining("already exists");
    }

    @Test
    void renamesAndRetiresAUnit() {
        when(repository.findByOrgKeyAndCode(ORG, "pallet")).thenReturn(Optional.of(stored()));
        OrganizationUnit change = pallet();
        change.setName("Euro pallet");
        change.setStatus(UnitStatus.RETIRED);
        change.setVersion(3L);

        OrganizationUnit replaced = units.replace(ORG, "pallet", change);

        assertThat(replaced.getName()).isEqualTo("Euro pallet");
        assertThat(replaced.getStatus()).isEqualTo(UnitStatus.RETIRED);
        verify(guard).requireDesign(ORG);
    }

    @Test
    void refusesToChangeWhatStoredAmountsAreDerivedFrom() {
        when(repository.findByOrgKeyAndCode(ORG, "pallet")).thenReturn(Optional.of(stored()));
        OrganizationUnit otherFactor = pallet();
        otherFactor.setFactor(new BigDecimal("600"));
        OrganizationUnit otherKind = pallet();
        otherKind.setKind("LENGTH");
        OrganizationUnit otherCode = pallet();
        otherCode.setCode("crate");

        for (OrganizationUnit change : List.of(otherFactor, otherKind, otherCode)) {
            assertThatThrownBy(() -> units.replace(ORG, "pallet", change)).isInstanceOf(ConflictException.class);
        }
    }

    @Test
    void refusesAStaleVersionAndAnUnknownUnit() {
        when(repository.findByOrgKeyAndCode(ORG, "pallet")).thenReturn(Optional.of(stored()));
        OrganizationUnit stale = pallet();
        stale.setVersion(2L);
        assertThatThrownBy(() -> units.replace(ORG, "pallet", stale)).isInstanceOf(ConflictException.class).hasMessageContaining("changed by someone");
        assertThatThrownBy(() -> units.replace(ORG, "missing", pallet())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void readingTakesAccessToTheOrganization() {
        when(repository.findByOrgKeyAndCode(ORG, "pallet")).thenReturn(Optional.of(stored()));
        when(repository.findAllByOrgKey(ORG, PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(stored())));
        when(repository.findAllByOrgKeyAndStatus(ORG, UnitStatus.RETIRED, PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of()));

        assertThat(units.find(ORG, "pallet").getCode()).isEqualTo("pallet");
        assertThat(units.list(ORG, null, PageRequest.of(0, 20)).getContent()).hasSize(1);
        assertThat(units.list(ORG, UnitStatus.RETIRED, PageRequest.of(0, 20)).getContent()).isEmpty();
        verify(guard, org.mockito.Mockito.times(3)).requireAccess(ORG);
        verify(guard, never()).requireDesign(ORG);
    }

    @Test
    void aRowThatCannotJoinTheCatalogIsLeftOutAndTheOthersStay() {
        OrganizationUnit clash = stored();
        clash.setCode("kg");
        OrganizationUnit fine = stored();
        when(repository.findAllByOrgKeyOrderByCode(ORG)).thenReturn(List.of(clash, fine));

        UnitCatalog catalog = catalogs.forOrganization(ORG);

        assertThat(catalog.unit("kg").factor()).isEqualByComparingTo("1");
        assertThat(catalog.findUnit("pallet")).isPresent();
        assertThat(catalogs.forOrganization(null)).isSameAs(UnitCatalog.standard());
    }
}
