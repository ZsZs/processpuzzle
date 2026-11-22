package com.processpuzzle.baseentity.exchange.adapters.inbound;

import com.processpuzzle.baseentity.exchange.OrganizationExchangeRates;
import com.processpuzzle.baseentity.exchange.domain.OrganizationExchangeRate;
import com.processpuzzle.baseentity.exchange.domain.OrganizationExchangeRateRepository;
import com.processpuzzle.baseentity.exchange.usecases.inbound.ManageExchangeRates;
import com.processpuzzle.baseentity.model.ExchangeRateInput;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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

class ExchangeRateAdapterTest {

    private static final LocalDate DAY = LocalDate.parse("2026-03-01");

    private final ManageExchangeRates rates = mock(ManageExchangeRates.class);
    private final ExchangeRateMapper mapper = new ExchangeRateMapper();
    private final ExchangeRateEndpoint endpoint = new ExchangeRateEndpoint(rates, mapper);

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static OrganizationExchangeRate stored() {
        OrganizationExchangeRate rate = new OrganizationExchangeRate();
        rate.setId(UUID.randomUUID());
        rate.setFromCurrency("EUR");
        rate.setToCurrency("USD");
        rate.setRate(new BigDecimal("1.10"));
        rate.setValidFrom(DAY);
        return rate;
    }

    private static ExchangeRateInput input() {
        return new ExchangeRateInput().from("EUR").to("USD").rate("1.10").validFrom(DAY);
    }

    @Test
    void anInputBecomesARateWithItsDecimalRead() {
        OrganizationExchangeRate rate = mapper.toDomain(input().version(3));

        assertThat(rate.getRate()).isEqualByComparingTo("1.1");
        assertThat(rate.getFromCurrency()).isEqualTo("EUR");
        assertThat(rate.getValidFrom()).isEqualTo(DAY);
        assertThat(rate.getVersion()).isEqualTo(3L);
        assertThat(mapper.toDomain(input()).getVersion()).isNull();
    }

    @Test
    void aMalformedDecimalIsLeftForTheViolationsToReport() {
        OrganizationExchangeRate rate = mapper.toDomain(input().rate("a lot"));

        assertThat(rate.getRate()).isNull();
        assertThat(rate.violations()).extracting("attributeCode").contains("rate");
    }

    @Test
    void aRateIsMappedWithItsAuditAndVersionWhenItHasThem() {
        OrganizationExchangeRate rate = stored();
        assertThat(mapper.toModel(rate).getVersion()).isNull();
        assertThat(mapper.toModel(rate).getCreatedAt()).isNull();

        rate.setVersion(2L);
        rate.setCreatedAt(Instant.parse("2026-03-01T10:00:00Z"));
        rate.setUpdatedAt(Instant.parse("2026-03-02T10:00:00Z"));
        var model = mapper.toModel(rate);

        assertThat(model.getRate()).isEqualTo("1.1");
        assertThat(model.getVersion()).isEqualTo(2);
        assertThat(model.getCreatedAt()).hasToString("2026-03-01T10:00Z");
        assertThat(model.getUpdatedAt()).hasToString("2026-03-02T10:00Z");
    }

    @Test
    void listingDefaultsToTheFirstPageOfTwentyNewestFirstWithinAPair() {
        when(rates.list(eq("acme"), any(), any(), any())).thenReturn(new PageImpl<>(List.of(stored()), PageRequest.of(1, 5), 6));

        var page = endpoint.listExchangeRates("acme", null, null, null, null).getBody();

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalPages()).isEqualTo(2);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(rates).list(eq("acme"), eq(null), eq(null), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().toString()).isEqualTo("fromCurrency: ASC,toCurrency: ASC,validFrom: DESC");
    }

    @Test
    void listingPassesThePairAndPage() {
        when(rates.list(eq("acme"), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        endpoint.listExchangeRates("acme", "EUR", "USD", 2, 10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(rates).list(eq("acme"), eq("EUR"), eq("USD"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void creatingAnswersCreatedWithTheRatesLocation() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("POST", "/organizations/acme/exchange-rates")));
        OrganizationExchangeRate created = stored();
        when(rates.create(eq("acme"), any(OrganizationExchangeRate.class))).thenReturn(created);

        var response = endpoint.createExchangeRate("acme", input());

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation()).hasToString("http://localhost/organizations/acme/exchange-rates/" + created.getId());
        assertThat(response.getBody().getFrom()).isEqualTo("EUR");
    }

    @Test
    void aRateIsReadReplacedAndDeletedById() {
        OrganizationExchangeRate rate = stored();
        when(rates.find("acme", rate.getId())).thenReturn(rate);
        when(rates.replace(eq("acme"), eq(rate.getId()), any(OrganizationExchangeRate.class))).thenReturn(rate);

        assertThat(endpoint.getExchangeRate("acme", rate.getId()).getBody().getTo()).isEqualTo("USD");
        assertThat(endpoint.replaceExchangeRate("acme", rate.getId(), input()).getBody().getTo()).isEqualTo("USD");
        assertThat(endpoint.deleteExchangeRate("acme", rate.getId()).getStatusCode().value()).isEqualTo(204);
        verify(rates).delete("acme", rate.getId());
    }

    @Test
    void anErasedTenantsRatesAreDeletedAndItsTableDropped() {
        OrganizationExchangeRateRepository repository = mock(OrganizationExchangeRateRepository.class);
        OrganizationExchangeRates tables = mock(OrganizationExchangeRates.class);

        new ExchangeRateTenantDataCleaner(repository, tables).onOrganizationDeleted(new OrganizationDeletedEvent("acme"));

        verify(repository).eraseByOrgKey("acme");
        verify(tables).evict("acme");
    }
}
