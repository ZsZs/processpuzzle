package com.processpuzzle.baseentity.unit.adapters.inbound;

import com.processpuzzle.baseentity.model.OrganizationUnitInput;
import com.processpuzzle.baseentity.model.OrganizationUnitStatus;
import com.processpuzzle.baseentity.unit.OrganizationUnitCatalogs;
import com.processpuzzle.baseentity.unit.domain.OrganizationUnit;
import com.processpuzzle.baseentity.unit.domain.OrganizationUnitRepository;
import com.processpuzzle.baseentity.unit.domain.UnitStatus;
import com.processpuzzle.baseentity.unit.usecases.inbound.ManageOrganizationUnits;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizationUnitAdapterTest {

    private final ManageOrganizationUnits units = mock(ManageOrganizationUnits.class);
    private final OrganizationUnitMapper mapper = new OrganizationUnitMapper();
    private final OrganizationUnitEndpoint endpoint = new OrganizationUnitEndpoint(units, mapper);

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
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

    private static OrganizationUnitInput input() {
        return new OrganizationUnitInput().code("pallet").symbol("plt").name("Pallet").kind("MASS").factor("500");
    }

    @Test
    void anInputBecomesAnActiveUnitWithADivisorOfOne() {
        OrganizationUnit unit = mapper.toDomain(input());

        assertThat(unit.getFactor()).isEqualByComparingTo("500");
        assertThat(unit.getDivisor()).isEqualByComparingTo("1");
        assertThat(unit.getStatus()).isEqualTo(UnitStatus.ACTIVE);
        assertThat(unit.getVersion()).isNull();
    }

    @Test
    void anInputKeepsItsDivisorStatusAndVersion() {
        OrganizationUnit unit = mapper.toDomain(input().divisor("4").status(OrganizationUnitStatus.RETIRED).version(3));

        assertThat(unit.getDivisor()).isEqualByComparingTo("4");
        assertThat(unit.getStatus()).isEqualTo(UnitStatus.RETIRED);
        assertThat(unit.getVersion()).isEqualTo(3L);
    }

    @Test
    void aMalformedDecimalIsLeftForTheViolationsToReport() {
        OrganizationUnit unit = mapper.toDomain(input().factor("heavy").divisor("many"));

        assertThat(unit.getFactor()).isNull();
        assertThat(unit.getDivisor()).isNull();
        assertThat(unit.violations()).extracting("attributeCode").contains("factor", "divisor");
    }

    @Test
    void aUnitWithoutAuditOrVersionIsMappedWithoutThem() {
        var model = mapper.toModel(pallet());

        assertThat(model.getCode()).isEqualTo("pallet");
        assertThat(model.getStatus()).isEqualTo(OrganizationUnitStatus.ACTIVE);
        assertThat(model.getVersion()).isNull();
        assertThat(model.getCreatedAt()).isNull();
        assertThat(model.getUpdatedAt()).isNull();
    }

    @Test
    void aStoredUnitIsMappedWithItsVersionAndAudit() {
        OrganizationUnit unit = pallet();
        unit.setVersion(2L);
        unit.setCreatedAt(Instant.parse("2026-11-01T10:00:00Z"));
        unit.setUpdatedAt(Instant.parse("2026-11-02T10:00:00Z"));

        var model = mapper.toModel(unit);

        assertThat(model.getVersion()).isEqualTo(2);
        assertThat(model.getCreatedAt()).hasToString("2026-11-01T10:00Z");
        assertThat(model.getUpdatedAt()).hasToString("2026-11-02T10:00Z");
    }

    @Test
    void aPageCarriesItsUnitsAndPosition() {
        var page = mapper.toPage(new PageImpl<>(List.of(pallet()), PageRequest.of(1, 5), 6));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(5);
        assertThat(page.getTotalElements()).isEqualTo(6);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void listingDefaultsToTheFirstPageOfTwentyAndAllStatuses() {
        when(units.list(eq("acme"), any(), any())).thenReturn(new PageImpl<>(List.of(pallet())));

        assertThat(endpoint.listOrganizationUnits("acme", null, null, null).getBody()).isNotNull();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(units).list(eq("acme"), eq(null), pageable.capture());
        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(0, 20));
    }

    @Test
    void listingPassesTheRequestedStatusAndPage() {
        when(units.list(eq("acme"), any(), any())).thenReturn(new PageImpl<>(List.of()));

        endpoint.listOrganizationUnits("acme", OrganizationUnitStatus.RETIRED, 2, 10);

        verify(units).list("acme", UnitStatus.RETIRED, PageRequest.of(2, 10));
    }

    @Test
    void creatingAnswersCreatedWithTheUnitsLocation() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("POST", "/organizations/acme/units")));
        when(units.create(eq("acme"), any(OrganizationUnit.class))).thenReturn(pallet());

        var response = endpoint.createOrganizationUnit("acme", input());

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation()).hasToString("http://localhost/organizations/acme/units/pallet");
        assertThat(response.getBody().getCode()).isEqualTo("pallet");
    }

    @Test
    void aUnitIsReadAndReplacedByItsCode() {
        when(units.find("acme", "pallet")).thenReturn(pallet());
        when(units.replace(eq("acme"), eq("pallet"), any(OrganizationUnit.class))).thenReturn(pallet());

        assertThat(endpoint.getOrganizationUnit("acme", "pallet").getBody().getCode()).isEqualTo("pallet");
        assertThat(endpoint.replaceOrganizationUnit("acme", "pallet", input()).getBody().getCode()).isEqualTo("pallet");
    }

    @Test
    void anErasedTenantsUnitsAreDeletedAndItsCatalogDropped() {
        OrganizationUnitRepository repository = mock(OrganizationUnitRepository.class);
        OrganizationUnitCatalogs catalogs = mock(OrganizationUnitCatalogs.class);

        new UnitTenantDataCleaner(repository, catalogs).onOrganizationDeleted(new OrganizationDeletedEvent("acme"));

        verify(repository).eraseByOrgKey("acme");
        verify(catalogs).evict("acme");
    }
}
