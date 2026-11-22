package com.processpuzzle.rule.usecase.engine;

import com.processpuzzle.core.quantity.Decimals;
import com.processpuzzle.core.quantity.Dimensions;
import com.processpuzzle.core.quantity.ExchangeRates;
import com.processpuzzle.core.quantity.FundamentalTypeException;
import com.processpuzzle.core.quantity.FundamentalTypeException.Reason;
import com.processpuzzle.core.quantity.Money;
import com.processpuzzle.core.quantity.Quantity;
import com.processpuzzle.core.quantity.UnitCatalog;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

/**
 * The measure helpers a rule expression can call: {@code qty(...)}, {@code money(...)} and {@code dims(...)}, which read a
 * literal ({@code qty('5 kg')}) or a stored value ({@code qty(entity.weight)}) as an object that converts and compares
 * itself, so a rule never does the unit arithmetic. The same surface exists in the browser's rule evaluator
 * ({@code measure-bindings.ts} in base-rule-frontend); {@code rule-measure-cases.json} is run against both.
 *
 * <p>The objects are host proxies over the {@code com.processpuzzle.core.quantity} classes — the arithmetic the backend
 * stores values with — and nothing else of the host is reachable from a rule. Read-only, like {@link EntityProxy}.
 * {@code qty(null)} is {@code null}, so a rule guards an absent value the way it guards any other.
 *
 * <p>A refusal — different kinds, different currencies, an unknown unit — is a {@link FundamentalTypeException}, which the
 * caller reports as a failed evaluation rather than a wrong answer.
 */
final class MeasureBindings {

    /**
     * The units a rule's values are read in: the catalog of the organization whose rule is evaluating, which the engine sets
     * before each evaluation ({@link #useCatalog}). One instance serves one context, and so one thread, at a time.
     */
    private UnitCatalog catalog = UnitCatalog.standard();

    /**
     * The exchange rates {@code money(...).to(...)} converts at: those of the organization whose rule is evaluating, set by the
     * engine before each evaluation like the catalog. None until told, so a conversion between two currencies is refused.
     */
    private ExchangeRates rates = ExchangeRates.none();

    /** Converts money at {@code rates} until told otherwise — the organization's, for the rule about to run. */
    void useRates(ExchangeRates rates) {
        this.rates = rates;
    }

    /** Reads units from {@code catalog} until told otherwise — the organization's, for the rule about to run. */
    void useCatalog(UnitCatalog catalog) {
        this.catalog = catalog;
    }

    /** Defines {@code qty}, {@code money} and {@code dims} as globals of the context. */
    void install(Context context) {
        Value globals = context.getBindings("js");
        globals.putMember("qty", (ProxyExecutable) args -> quantityOf(single("qty", args)));
        globals.putMember("money", (ProxyExecutable) args -> moneyOf(single("money", args)));
        globals.putMember("dims", (ProxyExecutable) args -> dimensionsOf(single("dims", args)));
    }

    // region the objects a rule sees

    private abstract static class MeasureObject implements ProxyObject {
        private final Map<String, Object> members = new LinkedHashMap<>();

        protected void member(String name, Object value) {
            members.put(name, value);
        }

        protected void method(String name, Function<Value[], Object> body) {
            members.put(name, (ProxyExecutable) body::apply);
        }

        @Override
        public Object getMember(String key) {
            return members.get(key);
        }

        @Override
        public Object getMemberKeys() {
            return ProxyArray.fromList(new ArrayList<>(members.keySet()));
        }

        @Override
        public boolean hasMember(String key) {
            return members.containsKey(key);
        }

        @Override
        public void putMember(String key, Value value) {
            throw new UnsupportedOperationException("Rule expressions are read-only; '" + key + "' cannot be modified from within a rule.");
        }
    }

    private final class QuantityObject extends MeasureObject {
        final Quantity quantity;

        QuantityObject(Quantity quantity) {
            this.quantity = quantity;
            member("amount", quantity.amountText());
            member("unit", quantity.unit());
            member("base", quantity.baseAmount(catalog).doubleValue());
            method("to", args -> new QuantityObject(quantity.convertTo(text("to", args), catalog)));
            method("plus", args -> new QuantityObject(quantity.plus(quantityArg(args), catalog)));
            method("minus", args -> new QuantityObject(quantity.minus(quantityArg(args), catalog)));
            method("times", args -> new QuantityObject(quantity.times(decimalArg(args), catalog)));
            method("dividedBy", args -> new QuantityObject(quantity.dividedBy(decimalArg(args), catalog)));
            method("eq", args -> quantity.compareTo(quantityArg(args), catalog) == 0);
            method("gt", args -> quantity.compareTo(quantityArg(args), catalog) > 0);
            method("ge", args -> quantity.compareTo(quantityArg(args), catalog) >= 0);
            method("lt", args -> quantity.compareTo(quantityArg(args), catalog) < 0);
            method("le", args -> quantity.compareTo(quantityArg(args), catalog) <= 0);
            method("toString", args -> quantity.amountText() + " " + quantity.unit());
            method("valueOf", args -> {
                throw operatorRefused("a quantity", "gt(), ge(), lt(), le() or eq(), e.g. qty(entity.weight).gt(qty('5 kg'))");
            });
        }
    }

    private final class MoneyObject extends MeasureObject {
        final Money money;

        MoneyObject(Money money) {
            this.money = money;
            member("amount", money.amountText());
            member("currency", money.currency());
            method("plus", args -> new MoneyObject(money.plus(moneyArg(args))));
            method("minus", args -> new MoneyObject(money.minus(moneyArg(args))));
            method("times", args -> new MoneyObject(money.times(decimalArg(args))));
            method("dividedBy", args -> new MoneyObject(money.dividedBy(decimalArg(args))));
            method("round", args -> new MoneyObject(money.rounded(catalog)));
            method("to", args -> new MoneyObject(converted(money, args)));
            method("eq", args -> money.compareTo(moneyArg(args)) == 0);
            method("gt", args -> money.compareTo(moneyArg(args)) > 0);
            method("ge", args -> money.compareTo(moneyArg(args)) >= 0);
            method("lt", args -> money.compareTo(moneyArg(args)) < 0);
            method("le", args -> money.compareTo(moneyArg(args)) <= 0);
            method("toString", args -> money.amountText() + " " + money.currency());
            method("valueOf", args -> {
                throw operatorRefused("money", "gt(), ge(), lt(), le() or eq(), e.g. money(entity.price).le(money('100 EUR'))");
            });
        }
    }

    private final class DimensionsObject extends MeasureObject {
        final Dimensions dimensions;

        DimensionsObject(Dimensions dimensions) {
            this.dimensions = dimensions;
            member("width", Decimals.text(dimensions.width()));
            member("height", Decimals.text(dimensions.height()));
            member("depth", dimensions.depth() == null ? null : Decimals.text(dimensions.depth()));
            member("unit", dimensions.unit());
            method("to", args -> new DimensionsObject(dimensions.convertTo(text("to", args), catalog)));
            method("area", args -> new QuantityObject(dimensions.area(catalog)));
            method("volume", args -> new QuantityObject(dimensions.volume(catalog)));
            method("toString", args -> dimensions.isThreeDimensional()
                    ? "%s x %s x %s %s".formatted(Decimals.text(dimensions.width()), Decimals.text(dimensions.height()), Decimals.text(dimensions.depth()), dimensions.unit())
                    : "%s x %s %s".formatted(Decimals.text(dimensions.width()), Decimals.text(dimensions.height()), dimensions.unit()));
            method("valueOf", args -> {
                throw operatorRefused("dimensions", "area() or volume(), then gt(), lt() ...");
            });
        }
    }

    // endregion

    /**
     * What a bare operator on a measure would be: {@code qty('10 kg') > qty('9 kg')} falls back to comparing the text, and
     * {@code qty('5 kg') > qty('5 m')} would ignore the kinds. Refused instead, with the way to ask the question.
     */
    private static UnsupportedOperationException operatorRefused(String what, String instead) {
        return new UnsupportedOperationException("%s cannot be used with an operator such as > or ==; use %s".formatted(what, instead));
    }

    // region reading what a rule hands in

    private Object quantityOf(Value value) {
        if (value.isNull()) {
            return null;
        }
        Object host = hostObject(value);
        if (host instanceof QuantityObject object) {
            return object;
        }
        if (value.isString()) {
            return new QuantityObject(Quantity.parse(value.asString(), catalog));
        }
        Map<String, Object> fields = fieldsOf(value, "quantity");
        return new QuantityObject(new Quantity(amount(fields.get("amount"), "amount"), string(fields.get("unit"), "unit")));
    }

    private Object moneyOf(Value value) {
        if (value.isNull()) {
            return null;
        }
        Object host = hostObject(value);
        if (host instanceof MoneyObject object) {
            return object;
        }
        if (value.isString()) {
            return new MoneyObject(Money.parse(value.asString(), catalog));
        }
        Map<String, Object> fields = fieldsOf(value, "money");
        return new MoneyObject(new Money(amount(fields.get("amount"), "amount"), string(fields.get("currency"), "currency")));
    }

    private Object dimensionsOf(Value value) {
        if (value.isNull()) {
            return null;
        }
        Object host = hostObject(value);
        if (host instanceof DimensionsObject object) {
            return object;
        }
        Map<String, Object> fields = fieldsOf(value, "dimensions");
        Object depth = fields.get("depth");
        return new DimensionsObject(new Dimensions(amount(fields.get("width"), "width"), amount(fields.get("height"), "height"),
                depth == null ? null : amount(depth, "depth"), string(fields.get("unit"), "unit")));
    }

    private Quantity quantityArg(Value[] args) {
        Value value = single("argument", args);
        Object host = hostObject(value);
        if (host instanceof QuantityObject object) {
            return object.quantity;
        }
        if (value.isString()) {
            return Quantity.parse(value.asString(), catalog);
        }
        throw new FundamentalTypeException(Reason.INVALID_AMOUNT, "expected a quantity, e.g. qty('5 kg')");
    }

    /**
     * The money in the currency of {@code args[0]} at the rate valid on the date of {@code args[1]} — an ISO date or date-time
     * text, or a JS date — or today's when there is none. Rounded to the target's minor units.
     */
    private Money converted(Money money, Value[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new FundamentalTypeException(Reason.INVALID_RATE, "to takes a currency code and, optionally, a date");
        }
        if (!args[0].isString()) {
            throw new FundamentalTypeException(Reason.UNKNOWN_CURRENCY, "to expects a currency code, e.g. 'USD'");
        }
        boolean dated = args.length == 2 && !args[1].isNull();
        return money.convertTo(args[0].asString(), rates, dated ? dateArg(args[1]) : LocalDate.now(), catalog);
    }

    private static LocalDate dateArg(Value value) {
        if (value.isDate()) {
            return value.asDate();
        }
        String text = value.isString() ? value.asString().trim() : "";
        try {
            return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
        } catch (DateTimeParseException e) {
            throw new FundamentalTypeException(Reason.INVALID_RATE, "expected an ISO date, e.g. '2026-07-01'", e);
        }
    }

    private Money moneyArg(Value[] args) {
        Value value = single("argument", args);
        Object host = hostObject(value);
        if (host instanceof MoneyObject object) {
            return object.money;
        }
        if (value.isString()) {
            return Money.parse(value.asString(), catalog);
        }
        throw new FundamentalTypeException(Reason.INVALID_AMOUNT, "expected money, e.g. money('100 EUR')");
    }

    /** A plain number or its decimal text; a JS number is read as the decimal it prints as, so {@code 0.1} is one tenth. */
    private static BigDecimal decimalArg(Value[] args) {
        Value value = single("argument", args);
        if (value.isString()) {
            return Decimals.parse(value.asString());
        }
        if (value.fitsInLong()) {
            return BigDecimal.valueOf(value.asLong());
        }
        if (value.isNumber() && Double.isFinite(value.asDouble())) {
            return BigDecimal.valueOf(value.asDouble());
        }
        throw new FundamentalTypeException(Reason.INVALID_AMOUNT, "expected a number");
    }

    private static String text(String what, Value[] args) {
        Value value = single(what, args);
        if (!value.isString()) {
            throw new FundamentalTypeException(Reason.UNKNOWN_UNIT, "%s expects a unit code, e.g. 'kg'".formatted(what));
        }
        return value.asString();
    }

    private static Value single(String what, Value[] args) {
        if (args.length != 1) {
            throw new FundamentalTypeException(Reason.INVALID_AMOUNT, "%s takes exactly one argument".formatted(what));
        }
        return args[0];
    }

    private static Object hostObject(Value value) {
        return value.isProxyObject() ? value.asProxyObject() : null;
    }

    /** The members of a stored value ({@code entity.weight}) or of an object literal a rule wrote ({@code {amount: 5, unit: 'kg'}}). */
    private static Map<String, Object> fieldsOf(Value value, String what) {
        if (hostObject(value) instanceof EntityProxy proxy) {
            return proxy.fields();
        }
        if (value.hasMembers() && !value.isProxyObject()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            for (String key : value.getMemberKeys()) {
                fields.put(key, plain(value.getMember(key)));
            }
            return fields;
        }
        throw new FundamentalTypeException(Reason.INVALID_AMOUNT, "not a %s: pass text such as qty('5 kg'), or a stored value".formatted(what));
    }

    /** A member of a rule's object literal as the plain Java value the readers below take; anything else reads as absent. */
    private static Object plain(Value member) {
        if (member.isString()) {
            return member.asString();
        }
        if (member.fitsInLong()) {
            return member.asLong();
        }
        return member.isNumber() ? member.asDouble() : null;
    }

    private static BigDecimal amount(Object raw, String field) {
        if (raw instanceof String text) {
            return Decimals.parse(text);
        }
        if (raw instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        throw new FundamentalTypeException(Reason.INVALID_AMOUNT, "%s must be a decimal number".formatted(field));
    }

    private static String string(Object raw, String field) {
        if (raw instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new FundamentalTypeException(Reason.INVALID_AMOUNT, "%s is missing".formatted(field));
    }

    // endregion
}
