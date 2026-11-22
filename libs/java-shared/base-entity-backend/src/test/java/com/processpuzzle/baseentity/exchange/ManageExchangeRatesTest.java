package com.processpuzzle.baseentity.exchange;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.exchange.domain.OrganizationExchangeRate;
import com.processpuzzle.baseentity.exchange.domain.OrganizationExchangeRateRepository;
import com.processpuzzle.baseentity.exchange.usecases.inbound.ManageExchangeRates;
import com.processpuzzle.core.quantity.ExchangeRates;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManageExchangeRatesTest {

    private static final String ORG = "acme";
    private static final LocalDate DAY = LocalDate.parse("2026-03-01");

    private OrganizationExchangeRateRepository repository;
    private OrganizationGuard guard;
    private OrganizationExchangeRates rates;
    private ManageExchangeRates manage;

    @BeforeEach
    void setUp() {
        repository = mock(OrganizationExchangeRateRepository.class);
        guard = mock(OrganizationGuard.class);
        rates = new OrganizationExchangeRates(repository);
        manage = new ManageExchangeRates(repository, rates, guard);
        when(repository.save(ArgumentMatchers.any(OrganizationExchangeRate.class))).thenAnswer(call -> call.getArgument(0));
        when(repository.saveAndFlush(ArgumentMatchers.any(OrganizationExchangeRate.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static OrganizationExchangeRate rate(String from, String to, String value, LocalDate validFrom) {
        OrganizationExchangeRate rate = new OrganizationExchangeRate();
        rate.setFromCurrency(from);
        rate.setToCurrency(to);
        rate.setRate(value == null ? null : new BigDecimal(value));
        rate.setValidFrom(validFrom);
        return rate;
    }

    private static OrganizationExchangeRate stored(UUID id) {
        OrganizationExchangeRate rate = rate("EUR", "USD", "1.1", DAY);
        rate.setId(id);
        rate.setOrgKey(ORG);
        rate.setVersion(2L);
        return rate;
    }

    @Test
    void creatingNeedsTheRightToDesignAndSavesInTheOrganization() {
        OrganizationExchangeRate saved = manage.create(ORG, rate("EUR", "USD", "1.1", DAY));

        verify(guard).requireDesign(ORG);
        assertThat(saved.getOrgKey()).isEqualTo(ORG);
    }

    @Test
    void aRateThatIsNotWellFormedIsRefusedWithEveryViolation() {
        assertThatThrownBy(() -> manage.create(ORG, rate("EUR", "EUR", "0", null)))
            .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getViolations()).extracting("attributeCode").contains("to", "rate", "validFrom"));
        assertThatThrownBy(() -> manage.create(ORG, rate("XXX", "ZZZ", null, DAY)))
            .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getViolations()).extracting("attributeCode").contains("from", "to", "rate"));
        verify(repository, never()).save(ArgumentMatchers.any());
    }

    @Test
    void aPairHasOneRatePerDate() {
        when(repository.findByOrgKeyAndFromCurrencyAndToCurrencyAndValidFrom(ORG, "EUR", "USD", DAY)).thenReturn(Optional.of(stored(UUID.randomUUID())));

        assertThatThrownBy(() -> manage.create(ORG, rate("EUR", "USD", "1.2", DAY))).isInstanceOf(ConflictException.class).hasMessageContaining("already has a rate");
    }

    @Test
    void aReplacedRateMayKeepItsOwnDateButNotTakeAnothersOfTheSamePair() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(stored(id)));
        when(repository.findByOrgKeyAndFromCurrencyAndToCurrencyAndValidFrom(ORG, "EUR", "USD", DAY)).thenReturn(Optional.of(stored(id)));

        OrganizationExchangeRate replaced = manage.replace(ORG, id, rate("EUR", "USD", "1.3", DAY));

        assertThat(replaced.getRate()).isEqualByComparingTo("1.3");
        when(repository.findByOrgKeyAndFromCurrencyAndToCurrencyAndValidFrom(ORG, "EUR", "USD", DAY)).thenReturn(Optional.of(stored(UUID.randomUUID())));
        assertThatThrownBy(() -> manage.replace(ORG, id, rate("EUR", "USD", "1.3", DAY))).isInstanceOf(ConflictException.class);
    }

    @Test
    void aStaleVersionIsAConflictAndAMissingRateIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(stored(id)));
        OrganizationExchangeRate stale = rate("EUR", "USD", "1.3", DAY);
        stale.setVersion(1L);

        assertThatThrownBy(() -> manage.replace(ORG, id, stale)).isInstanceOf(ConflictException.class).hasMessageContaining("changed by someone else");
        assertThatThrownBy(() -> manage.find(ORG, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deletingRemovesTheRateAndDropsTheCachedTable() {
        UUID id = UUID.randomUUID();
        OrganizationExchangeRate existing = stored(id);
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(existing));
        when(repository.findAllByOrgKey(ORG)).thenReturn(List.of(existing));
        ExchangeRates before = rates.forOrganization(ORG);

        manage.delete(ORG, id);

        verify(guard).requireDesign(ORG);
        verify(repository).delete(existing);
        assertThat(rates.forOrganization(ORG)).isNotSameAs(before);
    }

    @Test
    void listingTakesAccessAndNarrowsBySideOfThePair() {
        Pageable pageable = PageRequest.of(0, 20);
        when(repository.findAllByOrgKey(ORG, pageable)).thenReturn(new PageImpl<>(List.of()));
        when(repository.findAllByOrgKeyAndFromCurrency(ORG, "EUR", pageable)).thenReturn(new PageImpl<>(List.of()));
        when(repository.findAllByOrgKeyAndToCurrency(ORG, "USD", pageable)).thenReturn(new PageImpl<>(List.of()));
        when(repository.findAllByOrgKeyAndFromCurrencyAndToCurrency(ORG, "EUR", "USD", pageable)).thenReturn(new PageImpl<>(List.of()));

        manage.list(ORG, null, null, pageable);
        manage.list(ORG, " ", "", pageable);
        manage.list(ORG, "EUR", null, pageable);
        manage.list(ORG, null, "USD", pageable);
        manage.list(ORG, "EUR", "USD", pageable);

        verify(guard, org.mockito.Mockito.times(5)).requireAccess(ORG);
        verify(repository, org.mockito.Mockito.times(2)).findAllByOrgKey(ORG, pageable);
        verify(repository).findAllByOrgKeyAndFromCurrency(ORG, "EUR", pageable);
        verify(repository).findAllByOrgKeyAndToCurrency(ORG, "USD", pageable);
        verify(repository).findAllByOrgKeyAndFromCurrencyAndToCurrency(ORG, "EUR", "USD", pageable);
    }

    @Test
    void theProviderBuildsATableOnceAndAnOrganizationWithoutRatesHasNone() {
        when(repository.findAllByOrgKey(ORG)).thenReturn(List.of(stored(UUID.randomUUID()), rate("EUR", "EUR", "1", DAY)));

        ExchangeRates table = rates.forOrganization(ORG);

        assertThat(table.rate("EUR", "USD", DAY)).isEqualByComparingTo("1.1");
        assertThat(rates.forOrganization(ORG)).as("kept, and the unusable row left out").isSameAs(table);
        verify(repository, org.mockito.Mockito.times(1)).findAllByOrgKey(ORG);
        assertThat(rates.forOrganization(null)).isSameAs(ExchangeRates.none());
    }
}
