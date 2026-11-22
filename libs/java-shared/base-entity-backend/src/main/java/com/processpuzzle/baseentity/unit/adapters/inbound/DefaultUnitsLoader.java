package com.processpuzzle.baseentity.unit.adapters.inbound;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.baseentity.common.ValidationException.Violation;
import com.processpuzzle.baseentity.exchange.OrganizationExchangeRates;
import com.processpuzzle.baseentity.exchange.domain.OrganizationExchangeRate;
import com.processpuzzle.baseentity.exchange.domain.OrganizationExchangeRateRepository;
import com.processpuzzle.baseentity.unit.OrganizationUnitCatalogs;
import com.processpuzzle.baseentity.unit.domain.OrganizationUnit;
import com.processpuzzle.baseentity.unit.domain.OrganizationUnitRepository;
import com.processpuzzle.baseentity.unit.domain.UnitStatus;
import com.processpuzzle.core.quantity.UnitCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Seeds an organization's own units and exchange rates at startup, when {@code base-entity.loadDefaultEntities=true}.
 *
 * <p>A file is {@code default-units/<orgKey>-units.yaml}, found on {@code classpath*:} so a host application can contribute its
 * own beside this library's. It has two lists, {@code units} (code, symbol, name, kind, factor, divisor) and
 * {@code exchangeRates} (from, to, rate, validFrom).
 *
 * <p><strong>Existing data is never touched</strong>: a unit whose code the organization already has, and a rate for a pair and
 * date it already has, are left as they are — a designer's change to a seeded rate survives a restart. A unit or a rate that does
 * not validate is logged and skipped, and nothing here can fail startup.
 *
 * <p>Ordered ahead of {@code DefaultEntityLoader} ({@code @Order(10)}): a seeded definition may name a unit of the organization in
 * its {@code allowedUnits}, and a definition naming a unit the organization does not have yet is rejected.
 */
@Component
@ConditionalOnProperty(prefix = "base-entity", name = "loadDefaultEntities", havingValue = "true")
public class DefaultUnitsLoader {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultUnitsLoader.class);
    private static final String SUFFIX = "-units.yaml";
    private static final String LOCATION = "classpath*:default-units/*" + SUFFIX;

    /** A unit as the file declares it; the decimals are text, as everywhere on the wire. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record UnitEntry(String code, String symbol, String name, String kind, String factor, String divisor) {
    }

    /** An exchange rate as the file declares it. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RateEntry(String from, String to, String rate, String validFrom) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Document(List<UnitEntry> units, List<RateEntry> exchangeRates) {
        Document {
            units = units == null ? List.of() : units;
            exchangeRates = exchangeRates == null ? List.of() : exchangeRates;
        }
    }

    private final OrganizationUnitRepository units;
    private final OrganizationExchangeRateRepository rates;
    private final OrganizationUnitCatalogs catalogs;
    private final OrganizationExchangeRates tables;
    private final ResourcePatternResolver resourceResolver;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory()).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public DefaultUnitsLoader(OrganizationUnitRepository units, OrganizationExchangeRateRepository rates, OrganizationUnitCatalogs catalogs,
                              OrganizationExchangeRates tables, ResourcePatternResolver resourceResolver) {
        this.units = units;
        this.rates = rates;
        this.catalogs = catalogs;
        this.tables = tables;
        this.resourceResolver = resourceResolver;
    }

    @Order(5)
    @EventListener(ApplicationReadyEvent.class)
    public void loadDefaults() {
        Resource[] resources;
        try {
            resources = resourceResolver.getResources(LOCATION);
        } catch (IOException e) {
            LOG.warn("Unable to scan for default unit files at {}", LOCATION, e);
            return;
        }
        for (Resource resource : resources) {
            load(resource);
        }
    }

    private void load(Resource resource) {
        String fileName = resource.getFilename();
        if (fileName == null || !fileName.endsWith(SUFFIX) || fileName.length() == SUFFIX.length()) {
            LOG.warn("Skipping default unit file '{}': the name does not follow the '<orgKey>{}' convention.", fileName, SUFFIX);
            return;
        }
        String orgKey = fileName.substring(0, fileName.length() - SUFFIX.length());
        Document document;
        try (InputStream input = resource.getInputStream()) {
            document = yamlMapper.readValue(input, Document.class);
        } catch (IOException e) {
            LOG.warn("Failed to read default unit file {}", fileName, e);
            return;
        }
        int unitsCreated = (int) document.units().stream().filter(entry -> seedUnit(orgKey, entry, fileName)).count();
        int ratesCreated = (int) document.exchangeRates().stream().filter(entry -> seedRate(orgKey, entry, fileName)).count();
        catalogs.evict(orgKey);
        tables.evict(orgKey);
        LOG.info("Loaded default units from {} into organization '{}': {} of {} units and {} of {} exchange rates created, the rest present or rejected.",
            fileName, orgKey, unitsCreated, document.units().size(), ratesCreated, document.exchangeRates().size());
    }

    /** Whether the unit was created. */
    private boolean seedUnit(String orgKey, UnitEntry entry, String fileName) {
        if (entry == null || entry.code() == null) {
            LOG.warn("Skipping a unit in {}: the entry has no code.", fileName);
            return false;
        }
        if (units.existsByOrgKeyAndCode(orgKey, entry.code())) {
            return false;
        }
        OrganizationUnit unit = new OrganizationUnit();
        unit.setOrgKey(orgKey);
        unit.setCode(entry.code());
        unit.setSymbol(entry.symbol());
        unit.setName(entry.name());
        unit.setKind(entry.kind());
        unit.setFactor(OrganizationUnit.decimalOrNull(entry.factor()));
        unit.setDivisor(entry.divisor() == null ? BigDecimal.ONE : OrganizationUnit.decimalOrNull(entry.divisor()));
        unit.setStatus(UnitStatus.ACTIVE);
        List<Violation> violations = unit.violations();
        if (!violations.isEmpty()) {
            LOG.warn("Default unit '{}' from {} was rejected: {}", entry.code(), fileName, violations);
            return false;
        }
        UnitCatalog standard = UnitCatalog.standard();
        if (standard.kinds().stream().noneMatch(kind -> kind.code().equals(entry.kind())) || standard.findUnit(entry.code()).isPresent()) {
            LOG.warn("Default unit '{}' from {} was rejected: its kind is not one of the standard catalog, or its code is a standard unit.", entry.code(), fileName);
            return false;
        }
        return save(() -> units.save(unit), "unit '" + entry.code() + "'", fileName);
    }

    /** Whether the rate was created. */
    private boolean seedRate(String orgKey, RateEntry entry, String fileName) {
        if (entry == null) {
            LOG.warn("Skipping an exchange rate in {}: the entry is empty.", fileName);
            return false;
        }
        OrganizationExchangeRate rate = new OrganizationExchangeRate();
        rate.setOrgKey(orgKey);
        rate.setFromCurrency(entry.from());
        rate.setToCurrency(entry.to());
        rate.setRate(OrganizationUnit.decimalOrNull(entry.rate()));
        rate.setValidFrom(dateOrNull(entry.validFrom()));
        List<Violation> violations = rate.violations();
        if (!violations.isEmpty()) {
            LOG.warn("Default exchange rate {} to {} from {} was rejected: {}", entry.from(), entry.to(), fileName, violations);
            return false;
        }
        if (rates.findByOrgKeyAndFromCurrencyAndToCurrencyAndValidFrom(orgKey, entry.from(), entry.to(), rate.getValidFrom()).isPresent()) {
            return false;
        }
        return save(() -> rates.save(rate), "exchange rate " + entry.from() + " to " + entry.to() + " from " + entry.validFrom(), fileName);
    }

    private static boolean save(Runnable save, String what, String fileName) {
        try {
            save.run();
            return true;
        } catch (RuntimeException e) {
            LOG.warn("Failed to create default {} from {}.", what, fileName, e);
            return false;
        }
    }

    private static LocalDate dateOrNull(String text) {
        try {
            return text == null ? null : LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
