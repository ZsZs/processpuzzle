package com.processpuzzle.baseentity.exchange.usecases.inbound;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.common.ValidationException.Violation;
import com.processpuzzle.baseentity.exchange.OrganizationExchangeRates;
import com.processpuzzle.baseentity.exchange.domain.OrganizationExchangeRate;
import com.processpuzzle.baseentity.exchange.domain.OrganizationExchangeRateRepository;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds, reads, changes and deletes the exchange rates of an organization. Reading takes access to the organization (a form that
 * hints a converted amount needs them); changing takes the right to design. A change drops the organization's cached table.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class ManageExchangeRates {

    private final OrganizationExchangeRateRepository repository;
    private final OrganizationExchangeRates rates;
    private final OrganizationGuard guard;

    public OrganizationExchangeRate create(String orgKey, OrganizationExchangeRate rate) {
        guard.requireDesign(orgKey);
        rate.setOrgKey(orgKey);
        requireValid(rate);
        requireFree(orgKey, rate, null);
        OrganizationExchangeRate saved = repository.save(rate);
        rates.evict(orgKey);
        return saved;
    }

    public OrganizationExchangeRate replace(String orgKey, UUID id, OrganizationExchangeRate incoming) {
        guard.requireDesign(orgKey);
        OrganizationExchangeRate rate = find(orgKey, id);
        requireValid(incoming);
        if (incoming.getVersion() != null && !incoming.getVersion().equals(rate.getVersion())) {
            throw new ConflictException("Exchange rate '%s' was changed by someone else".formatted(id));
        }
        incoming.setOrgKey(orgKey);
        requireFree(orgKey, incoming, id);
        rate.setFromCurrency(incoming.getFromCurrency());
        rate.setToCurrency(incoming.getToCurrency());
        rate.setRate(incoming.getRate());
        rate.setValidFrom(incoming.getValidFrom());
        OrganizationExchangeRate saved = repository.saveAndFlush(rate);
        rates.evict(orgKey);
        return saved;
    }

    public void delete(String orgKey, UUID id) {
        guard.requireDesign(orgKey);
        repository.delete(find(orgKey, id));
        rates.evict(orgKey);
    }

    @Transactional(readOnly = true)
    public OrganizationExchangeRate find(String orgKey, UUID id) {
        guard.requireAccess(orgKey);
        return repository.findByIdAndOrgKey(id, orgKey).orElseThrow(() -> new NotFoundException("Exchange rate '%s' not found".formatted(id)));
    }

    /** The organization's rates, narrowed to a side of a pair when {@code from} or {@code to} is given. */
    @Transactional(readOnly = true)
    public Page<OrganizationExchangeRate> list(String orgKey, String from, String to, Pageable pageable) {
        guard.requireAccess(orgKey);
        boolean hasFrom = from != null && !from.isBlank();
        boolean hasTo = to != null && !to.isBlank();
        if (hasFrom && hasTo) {
            return repository.findAllByOrgKeyAndFromCurrencyAndToCurrency(orgKey, from, to, pageable);
        }
        if (hasFrom) {
            return repository.findAllByOrgKeyAndFromCurrency(orgKey, from, pageable);
        }
        return hasTo ? repository.findAllByOrgKeyAndToCurrency(orgKey, to, pageable) : repository.findAllByOrgKey(orgKey, pageable);
    }

    private static void requireValid(OrganizationExchangeRate rate) {
        List<Violation> violations = rate.violations();
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }

    /** A pair has one rate per date; {@code except} is the rate being replaced, which may keep its own date. */
    private void requireFree(String orgKey, OrganizationExchangeRate rate, UUID except) {
        repository.findByOrgKeyAndFromCurrencyAndToCurrencyAndValidFrom(orgKey, rate.getFromCurrency(), rate.getToCurrency(), rate.getValidFrom())
            .filter(existing -> !existing.getId().equals(except))
            .ifPresent(existing -> {
                throw new ConflictException("%s to %s already has a rate valid from %s".formatted(rate.getFromCurrency(), rate.getToCurrency(), rate.getValidFrom()));
            });
    }
}
