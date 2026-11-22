package com.processpuzzle.baseentity.exchange.adapters.inbound;

import com.processpuzzle.baseentity.exchange.domain.OrganizationExchangeRate;
import com.processpuzzle.baseentity.model.ExchangeRate;
import com.processpuzzle.baseentity.model.ExchangeRateInput;
import com.processpuzzle.baseentity.model.Page;
import com.processpuzzle.baseentity.unit.domain.OrganizationUnit;
import com.processpuzzle.core.quantity.Decimals;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Component;

/** Between the contract's rate and the domain's. The decimal travels as text and is read here, so a malformed one is a violation, not a 500. */
@Component
class ExchangeRateMapper {

    OrganizationExchangeRate toDomain(ExchangeRateInput input) {
        OrganizationExchangeRate rate = new OrganizationExchangeRate();
        rate.setFromCurrency(input.getFrom());
        rate.setToCurrency(input.getTo());
        rate.setRate(OrganizationUnit.decimalOrNull(input.getRate()));
        rate.setValidFrom(input.getValidFrom());
        rate.setVersion(input.getVersion() == null ? null : input.getVersion().longValue());
        return rate;
    }

    ExchangeRate toModel(OrganizationExchangeRate rate) {
        return new ExchangeRate()
            .id(rate.getId())
            .from(rate.getFromCurrency())
            .to(rate.getToCurrency())
            .rate(Decimals.text(rate.getRate()))
            .validFrom(rate.getValidFrom())
            .version(rate.getVersion() == null ? null : rate.getVersion().intValue())
            .createdAt(rate.getCreatedAt() == null ? null : OffsetDateTime.ofInstant(rate.getCreatedAt(), ZoneOffset.UTC))
            .createdBy(rate.getCreatedBy())
            .updatedAt(rate.getUpdatedAt() == null ? null : OffsetDateTime.ofInstant(rate.getUpdatedAt(), ZoneOffset.UTC))
            .updatedBy(rate.getUpdatedBy());
    }

    Page toPage(org.springframework.data.domain.Page<OrganizationExchangeRate> page) {
        List<Object> content = page.getContent().stream().map(this::toModel).map(Object.class::cast).toList();
        return new Page()
            .content(content)
            .page(page.getNumber())
            .size(page.getSize())
            .totalElements((int) page.getTotalElements())
            .totalPages(page.getTotalPages());
    }
}
