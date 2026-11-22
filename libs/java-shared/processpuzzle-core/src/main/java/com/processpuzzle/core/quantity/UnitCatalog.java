package com.processpuzzle.core.quantity;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The units and currencies a value may name. Unit codes are unique across kinds, so a code alone identifies the unit and
 * what it measures. {@link #standard()} reads {@code fundamental-types/standard-units.json} — the same file the frontend
 * imports from {@code @processpuzzle/util}.
 */
public final class UnitCatalog {

    static final String STANDARD_RESOURCE = "/fundamental-types/standard-units.json";

    /** A unit with the kind it measures: {@code base = (amount + offset) × factor / divisor}. */
    public record UnitDefinition(String code, String symbol, String name, String kind, BigDecimal factor, BigDecimal divisor, BigDecimal offset) {

        public boolean hasOffset() {
            return offset.signum() != 0;
        }
    }

    public record QuantityKindDefinition(String code, String name, String baseUnit, List<UnitDefinition> units) {
    }

    /** An ISO 4217 currency and the decimal places of its minor unit. */
    public record CurrencyDefinition(String code, int minorUnits) {
    }

    /** The catalog file's shape. */
    public record Data(List<KindData> kinds, List<CurrencyDefinition> currencies) {
    }

    public record KindData(String code, String name, String baseUnit, List<UnitData> units) {
    }

    public record UnitData(String code, String symbol, String name, String factor, String divisor, String offset) {
    }

    private static final class StandardHolder {
        private static final UnitCatalog STANDARD = load(UnitCatalog.class.getResourceAsStream(STANDARD_RESOURCE));
    }

    /** A unit an organization adds to a kind of the catalog: no offset (that is for temperature scales), and the factor is fixed. */
    public record ExtraUnit(String kind, String code, String symbol, String name, BigDecimal factor, BigDecimal divisor) {
    }

    private final Data data;
    private final Map<String, QuantityKindDefinition> kinds = new LinkedHashMap<>();
    private final Map<String, UnitDefinition> units = new LinkedHashMap<>();
    private final Map<String, CurrencyDefinition> currencies = new LinkedHashMap<>();

    public UnitCatalog(Data data) {
        this.data = data;
        data.kinds().forEach(this::addKind);
        data.currencies().forEach(currency -> {
            if (currencies.putIfAbsent(currency.code(), currency) != null) {
                throw invalid("currency %s is declared twice".formatted(currency.code()));
            }
        });
    }

    public static UnitCatalog standard() {
        return StandardHolder.STANDARD;
    }

    /**
     * This catalog with {@code extras} added to the kinds they name. A new catalog: this one is immutable and is shared.
     * An unknown kind, or a code the catalog already has (whatever its kind), is an {@code INVALID_CATALOG} failure —
     * the caller decides which of its rows to leave out.
     */
    public UnitCatalog withUnits(List<ExtraUnit> extras) {
        if (extras.isEmpty()) {
            return this;
        }
        Map<String, List<UnitData>> added = new LinkedHashMap<>();
        for (ExtraUnit extra : extras) {
            if (!kinds.containsKey(extra.kind())) {
                throw invalid("unit %s names the unknown kind %s".formatted(extra.code(), extra.kind()));
            }
            added.computeIfAbsent(extra.kind(), kind -> new ArrayList<>())
                .add(new UnitData(extra.code(), extra.symbol(), extra.name(), extra.factor().toPlainString(), extra.divisor().toPlainString(), null));
        }
        List<KindData> merged = data.kinds().stream().map(kind -> {
            List<UnitData> more = added.get(kind.code());
            if (more == null) {
                return kind;
            }
            List<UnitData> all = new ArrayList<>(kind.units());
            all.addAll(more);
            return new KindData(kind.code(), kind.name(), kind.baseUnit(), List.copyOf(all));
        }).toList();
        return new UnitCatalog(new Data(merged, data.currencies()));
    }

    static UnitCatalog load(InputStream source) {
        if (source == null) {
            throw invalid("the unit catalog resource is missing");
        }
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (source) {
            return new UnitCatalog(mapper.readValue(source, Data.class));
        } catch (IOException e) {
            throw new FundamentalTypeException(FundamentalTypeException.Reason.INVALID_CATALOG, "the unit catalog cannot be read", e);
        }
    }

    public List<QuantityKindDefinition> kinds() {
        return List.copyOf(kinds.values());
    }

    public QuantityKindDefinition kind(String code) {
        QuantityKindDefinition kind = kinds.get(code);
        if (kind == null) {
            throw new FundamentalTypeException(FundamentalTypeException.Reason.UNKNOWN_KIND, "unknown quantity kind '%s'".formatted(code));
        }
        return kind;
    }

    public Optional<UnitDefinition> findUnit(String code) {
        return Optional.ofNullable(units.get(code));
    }

    public UnitDefinition unit(String code) {
        return findUnit(code).orElseThrow(() -> new FundamentalTypeException(FundamentalTypeException.Reason.UNKNOWN_UNIT, "unknown unit '%s'".formatted(code)));
    }

    public List<UnitDefinition> unitsOf(String kind) {
        return kind(kind).units();
    }

    public List<CurrencyDefinition> currencies() {
        return List.copyOf(currencies.values());
    }

    public Optional<CurrencyDefinition> findCurrency(String code) {
        return Optional.ofNullable(currencies.get(code));
    }

    public CurrencyDefinition currency(String code) {
        return findCurrency(code).orElseThrow(() -> new FundamentalTypeException(FundamentalTypeException.Reason.UNKNOWN_CURRENCY, "unknown currency '%s'".formatted(code)));
    }

    private void addKind(KindData data) {
        if (kinds.containsKey(data.code())) {
            throw invalid("kind %s is declared twice".formatted(data.code()));
        }
        List<UnitDefinition> kindUnits = data.units().stream().map(unit -> addUnit(data.code(), unit)).toList();
        boolean properBase = kindUnits.stream().anyMatch(unit -> unit.code().equals(data.baseUnit())
            && unit.factor().compareTo(BigDecimal.ONE) == 0 && unit.divisor().compareTo(BigDecimal.ONE) == 0 && !unit.hasOffset());
        if (!properBase) {
            throw invalid("kind %s must declare its base unit %s with factor 1".formatted(data.code(), data.baseUnit()));
        }
        kinds.put(data.code(), new QuantityKindDefinition(data.code(), data.name(), data.baseUnit(), kindUnits));
    }

    private UnitDefinition addUnit(String kind, UnitData data) {
        UnitDefinition unit = new UnitDefinition(data.code(), data.symbol(), data.name(), kind, Decimals.parse(data.factor()),
            data.divisor() == null ? BigDecimal.ONE : Decimals.parse(data.divisor()), data.offset() == null ? BigDecimal.ZERO : Decimals.parse(data.offset()));
        if (units.putIfAbsent(unit.code(), unit) != null) {
            throw invalid("unit %s is declared twice".formatted(unit.code()));
        }
        return unit;
    }

    private static FundamentalTypeException invalid(String message) {
        return new FundamentalTypeException(FundamentalTypeException.Reason.INVALID_CATALOG, message);
    }
}
