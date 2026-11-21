package com.processpuzzle.baseentity.instances.domain;

import com.processpuzzle.baseentity.instances.usecases.outbound.MeasureView;
import com.processpuzzle.core.quantity.Decimals;
import com.processpuzzle.core.quantity.Dimensions;
import com.processpuzzle.core.quantity.FundamentalTypeException;
import com.processpuzzle.core.quantity.Money;
import com.processpuzzle.core.quantity.Quantity;
import com.processpuzzle.core.quantity.UnitCatalog;
import com.processpuzzle.core.quantity.UnitCatalog.UnitDefinition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The value of a QUANTITY, MONEY or DIMENSIONS attribute: what makes one valid, and the form it is stored in.
 *
 * <p>The stored form is the submitted value with its amounts as canonical decimal text — {@code "12.50"} becomes
 * {@code "12.5"} — plus the numbers a query compares: {@code baseAmount} (the amount in the kind's base unit for a
 * quantity, the amount itself for money) and {@code baseArea} / {@code baseVolume} (m² and m³) for dimensions. They are
 * derived on every write and anything a client sends under those names is replaced, so they never disagree with the
 * amounts.
 *
 * <p>The derived numbers are {@code double}s on purpose: the payload is JSON, and a stored decimal reads back as a
 * Double. A BigDecimal here would compare unequal to its own stored copy, so every save of an unchanged object would
 * rewrite it and bump its version. A double prints its shortest exact decimal, so a value of up to fifteen significant
 * digits is stored as typed; the text amounts, which a client reads, stay exact.
 */
public final class MeasureValues {

    public static final String AMOUNT = "amount";
    public static final String UNIT = "unit";
    public static final String CURRENCY = "currency";
    public static final String BASE_AMOUNT = "baseAmount";
    public static final String BASE_AREA = "baseArea";
    public static final String BASE_VOLUME = "baseVolume";

    private static final String LENGTH = "LENGTH";
    private static final List<String> AXES = List.of("width", "height", "depth");

    private final UnitCatalog catalog = UnitCatalog.standard();
    private final MeasureView measure;
    private final Map<?, ?> value;
    private final List<String> problems = new ArrayList<>();

    private MeasureValues(MeasureView measure, Map<?, ?> value) {
        this.measure = measure;
        this.value = value;
    }

    /** Why {@code raw} is not a valid value of the attribute; empty when it is. */
    public static List<String> problems(MeasureView measure, Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return List.of("expected a %s object".formatted(measure.kind().name().toLowerCase(Locale.ROOT)));
        }
        return new MeasureValues(measure, map).check();
    }

    /** The value in its stored form. {@code raw} must have passed {@link #problems}. */
    public static Map<String, Object> normalized(MeasureView measure, Object raw) {
        return new MeasureValues(measure, (Map<?, ?>) raw).stored();
    }

    /**
     * The value a caller with a bare result — a calculation's number — writes to the attribute, in the stored form. A number is
     * taken to be in the attribute's default unit or currency and rounded half up to its decimal places (a money amount: the
     * currency's minor units), since the caller derived it and cannot know them; a measure object is taken as it is and must be
     * valid. Refused, with the reasons, when it is neither.
     *
     * @throws IllegalArgumentException when the value is no valid value of the attribute
     */
    public static Map<String, Object> forWrite(MeasureView measure, Object value) {
        MeasureValues measureValues = new MeasureValues(measure, Map.of());
        Map<?, ?> candidate = value instanceof Map<?, ?> map ? map : measureValues.bareAmount(value);
        List<String> problems = problems(measure, candidate);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
        return normalized(measure, candidate);
    }

    /** A bare number as the measure object the attribute holds: its amount, rounded, and the unit or currency it is taken to be in. */
    private Map<String, Object> bareAmount(Object value) {
        BigDecimal amount = amountOf(value).orElseThrow(() ->
                new IllegalArgumentException("expected a number or a %s object".formatted(measure.kind().name().toLowerCase(Locale.ROOT))));
        Map<String, Object> bare = new LinkedHashMap<>();
        switch (measure.kind()) {
            case QUANTITY -> {
                bare.put(AMOUNT, Decimals.text(roundedTo(amount, measure.scale())));
                bare.put(UNIT, defaultUnit());
            }
            case MONEY -> {
                String currency = defaultCurrency();
                bare.put(AMOUNT, Decimals.text(roundedTo(amount, catalog.findCurrency(currency).map(UnitCatalog.CurrencyDefinition::minorUnits).orElse(null))));
                bare.put(CURRENCY, currency);
            }
            default -> throw new IllegalArgumentException("dimensions are lengths and a unit, not a number: write {width, height, depth?, unit}");
        }
        return bare;
    }

    private static BigDecimal roundedTo(BigDecimal amount, Integer scale) {
        return scale == null ? amount : amount.setScale(scale, RoundingMode.HALF_UP);
    }

    /** The attribute's default unit when it may be used, else the base unit of what it may use, else the first of those. */
    private String defaultUnit() {
        List<String> allowed = measure.allowedUnits() == null ? List.of() : measure.allowedUnits();
        List<String> candidates = allowed.isEmpty() ? unitsOfQuantityKind() : allowed;
        String wanted = measure.defaultUnit();
        if (wanted != null && catalog.findUnit(wanted).isPresent() && (candidates.isEmpty() || candidates.contains(wanted))) {
            return wanted;
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("the attribute names no default unit and no quantity kind, so a number alone has no unit");
        }
        return candidates.stream().filter(this::isBaseUnit).findFirst().orElse(candidates.get(0));
    }

    private List<String> unitsOfQuantityKind() {
        return measure.quantityKind() == null ? List.of() : catalog.kinds().stream()
                .filter(kind -> kind.code().equals(measure.quantityKind()))
                .flatMap(kind -> kind.units().stream().map(UnitDefinition::code))
                .toList();
    }

    private boolean isBaseUnit(String code) {
        return catalog.findUnit(code).map(unit -> catalog.kind(unit.kind()).baseUnit().equals(unit.code())).orElse(false);
    }

    /** The attribute's default currency when it may be used, else the first allowed one. */
    private String defaultCurrency() {
        List<String> allowed = measure.allowedCurrencies() == null ? List.of() : measure.allowedCurrencies();
        String wanted = measure.defaultCurrency();
        if (wanted != null && catalog.findCurrency(wanted).isPresent() && (allowed.isEmpty() || allowed.contains(wanted))) {
            return wanted;
        }
        if (allowed.isEmpty()) {
            throw new IllegalArgumentException("the attribute names no default currency, so a number alone has no currency");
        }
        return allowed.get(0);
    }

    private List<String> check() {
        switch (measure.kind()) {
            case QUANTITY -> {
                checkAmount(AMOUNT, measure.scale());
                checkUnit(UNIT, this::quantityUnitAllowed);
            }
            case MONEY -> {
                checkAmount(AMOUNT, minorUnitsOfCurrency());
                checkCurrency();
            }
            default -> checkDimensions();
        }
        return problems;
    }

    private void checkDimensions() {
        for (String axis : AXES) {
            boolean present = value.get(axis) != null;
            boolean expected = !"depth".equals(axis) || measure.threeDimensional();
            if (expected) {
                checkAmount(axis, measure.scale());
            } else if (present) {
                problems.add("dimensions has no depth: the attribute has two axes");
            }
        }
        checkUnit(UNIT, this::dimensionUnitAllowed);
    }

    private void checkAmount(String field, Integer scale) {
        Optional<BigDecimal> amount = amountOf(value.get(field));
        if (amount.isEmpty()) {
            problems.add("%s must be a decimal number".formatted(field));
        } else if (scale != null && Math.max(0, Decimals.normalize(amount.get()).scale()) > scale) {
            problems.add("%s has more than %d decimal places".formatted(field, scale));
        }
    }

    private void checkUnit(String field, Predicate<UnitDefinition> allowed) {
        if (!(value.get(field) instanceof String code)) {
            problems.add("%s must be a unit code".formatted(field));
            return;
        }
        Optional<UnitDefinition> unit = catalog.findUnit(code);
        if (unit.isEmpty()) {
            problems.add("unit '%s' is unknown".formatted(code));
        } else if (!allowed.test(unit.get())) {
            problems.add("unit '%s' is not allowed here".formatted(code));
        }
    }

    private void checkCurrency() {
        if (!(value.get(CURRENCY) instanceof String code)) {
            problems.add("currency must be an ISO 4217 code");
        } else if (catalog.findCurrency(code).isEmpty()) {
            problems.add("currency '%s' is unknown".formatted(code));
        } else if (measure.allowedCurrencies() != null && !measure.allowedCurrencies().isEmpty() && !measure.allowedCurrencies().contains(code)) {
            problems.add("currency '%s' is not allowed here".formatted(code));
        }
    }

    private boolean quantityUnitAllowed(UnitDefinition unit) {
        List<String> allowed = measure.allowedUnits();
        if (allowed != null && !allowed.isEmpty()) {
            return allowed.contains(unit.code());
        }
        return measure.quantityKind() == null || measure.quantityKind().equals(unit.kind());
    }

    private boolean dimensionUnitAllowed(UnitDefinition unit) {
        List<String> allowed = measure.allowedUnits();
        return LENGTH.equals(unit.kind()) && (allowed == null || allowed.isEmpty() || allowed.contains(unit.code()));
    }

    /** A money amount has the currency's minor units as its decimal places; null while the currency is not yet known. */
    private Integer minorUnitsOfCurrency() {
        return value.get(CURRENCY) instanceof String code ? catalog.findCurrency(code).map(UnitCatalog.CurrencyDefinition::minorUnits).orElse(null) : null;
    }

    private Map<String, Object> stored() {
        Map<String, Object> stored = new LinkedHashMap<>();
        switch (measure.kind()) {
            case QUANTITY -> {
                Quantity quantity = new Quantity(requiredAmount(AMOUNT), (String) value.get(UNIT));
                stored.put(AMOUNT, quantity.amountText());
                stored.put(UNIT, quantity.unit());
                stored.put(BASE_AMOUNT, quantity.baseAmount(catalog).doubleValue());
            }
            case MONEY -> {
                Money money = new Money(requiredAmount(AMOUNT), (String) value.get(CURRENCY));
                stored.put(AMOUNT, money.amountText());
                stored.put(CURRENCY, money.currency());
                stored.put(BASE_AMOUNT, money.amount().doubleValue());
            }
            default -> storeDimensions(stored);
        }
        return stored;
    }

    private void storeDimensions(Map<String, Object> stored) {
        BigDecimal depth = measure.threeDimensional() ? requiredAmount("depth") : null;
        Dimensions dimensions = new Dimensions(requiredAmount("width"), requiredAmount("height"), depth, (String) value.get(UNIT));
        stored.put("width", Decimals.text(dimensions.width()));
        stored.put("height", Decimals.text(dimensions.height()));
        if (depth != null) {
            stored.put("depth", Decimals.text(dimensions.depth()));
        }
        stored.put(UNIT, dimensions.unit());
        stored.put(BASE_AREA, dimensions.area(catalog).amount().doubleValue());
        if (depth != null) {
            stored.put(BASE_VOLUME, dimensions.volume(catalog).amount().doubleValue());
        }
    }

    private BigDecimal requiredAmount(String field) {
        return amountOf(value.get(field)).orElseThrow(() -> new IllegalStateException("'%s' was validated but is not a decimal".formatted(field)));
    }

    /** An amount as a decimal: decimal text, or a finite number — a hand-written payload may hold one. */
    private static Optional<BigDecimal> amountOf(Object raw) {
        try {
            if (raw instanceof String text) {
                return Optional.of(Decimals.parse(text));
            }
            if (raw instanceof Double || raw instanceof Float) {
                double number = ((Number) raw).doubleValue();
                return Double.isFinite(number) ? Optional.of(BigDecimal.valueOf(number)) : Optional.empty();
            }
            return raw instanceof Number number ? Optional.of(new BigDecimal(number.toString())) : Optional.empty();
        } catch (FundamentalTypeException | NumberFormatException e) {
            return Optional.empty();
        }
    }
}
