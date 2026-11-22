package com.processpuzzle.baseentity.exchange.adapters.inbound;

import com.processpuzzle.baseentity.api.BaseEntityExchangeRatesApi;
import com.processpuzzle.baseentity.exchange.usecases.inbound.ManageExchangeRates;
import com.processpuzzle.baseentity.model.ExchangeRate;
import com.processpuzzle.baseentity.model.ExchangeRateInput;
import com.processpuzzle.baseentity.model.Page;
import com.processpuzzle.core.logging.LogClass;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Inbound REST adapter of the organization's exchange rates, implementing the generated {@link BaseEntityExchangeRatesApi}. */
@RestController
@LogClass
@RequiredArgsConstructor
public class ExchangeRateEndpoint implements BaseEntityExchangeRatesApi {

    /** A pair's rates together, the newest first. */
    private static final Sort ORDER = Sort.by("fromCurrency", "toCurrency").and(Sort.by(Sort.Direction.DESC, "validFrom"));

    private final ManageExchangeRates rates;
    private final ExchangeRateMapper mapper;

    @Override
    public ResponseEntity<Page> listExchangeRates(String orgKey, String from, String to, Integer page, Integer size) {
        PageRequest request = PageRequest.of(page != null ? page : 0, size != null ? size : 20, ORDER);
        return ResponseEntity.ok(mapper.toPage(rates.list(orgKey, from, to, request)));
    }

    @Override
    public ResponseEntity<ExchangeRate> createExchangeRate(String orgKey, ExchangeRateInput input) {
        var created = rates.create(orgKey, mapper.toDomain(input));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toModel(created));
    }

    @Override
    public ResponseEntity<ExchangeRate> getExchangeRate(String orgKey, UUID id) {
        return ResponseEntity.ok(mapper.toModel(rates.find(orgKey, id)));
    }

    @Override
    public ResponseEntity<ExchangeRate> replaceExchangeRate(String orgKey, UUID id, ExchangeRateInput input) {
        return ResponseEntity.ok(mapper.toModel(rates.replace(orgKey, id, mapper.toDomain(input))));
    }

    @Override
    public ResponseEntity<Void> deleteExchangeRate(String orgKey, UUID id) {
        rates.delete(orgKey, id);
        return ResponseEntity.noContent().build();
    }
}
