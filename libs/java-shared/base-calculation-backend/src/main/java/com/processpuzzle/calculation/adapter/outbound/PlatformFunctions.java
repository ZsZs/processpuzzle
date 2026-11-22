package com.processpuzzle.calculation.adapter.outbound;

import com.processpuzzle.calculation.engine.ExchangeRateScope;
import com.processpuzzle.core.quantity.FundamentalTypeException;
import com.processpuzzle.core.quantity.Money;
import com.processpuzzle.core.quantity.UnitCatalog;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import org.camunda.feel.context.JavaFunction;
import org.camunda.feel.context.JavaFunctionProvider;
import org.camunda.feel.syntaxtree.Val;
import org.camunda.feel.syntaxtree.ValDate;
import org.camunda.feel.syntaxtree.ValDateTime;
import org.camunda.feel.syntaxtree.ValLocalDateTime;
import org.camunda.feel.syntaxtree.ValError;
import org.camunda.feel.syntaxtree.ValList;
import org.camunda.feel.syntaxtree.ValNumber;
import org.camunda.feel.syntaxtree.ValString;
import scala.jdk.javaapi.CollectionConverters;

/**
 * The platform's FEEL functions over lists of numbers, for what FEEL's built-ins leave awkward:
 * <ul>
 *   <li>{@code drop worst(list, n)} — the list without its {@code n} highest values, the worst under a low-point
 *       system;</li>
 *   <li>{@code drop best(list, n)} — the list without its {@code n} lowest values;</li>
 *   <li>{@code top(list, n)} — the {@code n} highest values, highest first;</li>
 *   <li>{@code convert currency(amount, from, to[, date])} — the amount in another currency at the exchange rate of the organization
 *       the calculation runs for, valid on the date (today's without one), rounded to the target's minor units. A pair with no
 *       rate on that date is an error, never a guess.</li>
 * </ul>
 * Grouping and ranking with ties are decision types of their own ({@code aggregate}, {@code rank}), not functions.
 */
class PlatformFunctions extends JavaFunctionProvider {

    private static final Comparator<Val> NUMERIC = Comparator.comparing(value -> ((ValNumber) value).value().bigDecimal());

    private static final Map<String, BiFunction<List<Val>, Integer, List<Val>>> FUNCTIONS = Map.of(
            "drop worst", (values, n) -> sorted(values, NUMERIC).subList(0, Math.max(0, values.size() - n)),
            "drop best", (values, n) -> sorted(values, NUMERIC).subList(Math.min(n, values.size()), values.size()),
            "top", (values, n) -> sorted(values, NUMERIC.reversed()).subList(0, Math.min(n, values.size())));

    private static final String CONVERT = "convert currency";

    @Override
    public List<JavaFunction> resolveFunctions(String name) {
        if (CONVERT.equals(name)) {
            return List.of(
                    new JavaFunction(List.of("amount", "from", "to"), arguments -> convert(arguments, null)),
                    new JavaFunction(List.of("amount", "from", "to", "date"), arguments -> convert(arguments, arguments.get(3))));
        }
        return super.resolveFunctions(name);
    }

    @Override
    public Optional<JavaFunction> resolveFunction(String name) {
        BiFunction<List<Val>, Integer, List<Val>> function = FUNCTIONS.get(name);
        return function == null ? Optional.empty()
                : Optional.of(new JavaFunction(List.of("list", "n"), arguments -> apply(name, function, arguments)));
    }

    @Override
    public Collection<String> getFunctionNames() {
        Set<String> names = new LinkedHashSet<>(FUNCTIONS.keySet());
        names.add(CONVERT);
        return names;
    }

    private static Val convert(List<Val> arguments, Val date) {
        if (!(arguments.get(0) instanceof ValNumber amount) || !(arguments.get(1) instanceof ValString from) || !(arguments.get(2) instanceof ValString to)) {
            return new ValError("convert currency(amount, from, to[, date]) expects a number and two currency codes");
        }
        // A refusal is thrown, not returned as a ValError: FEEL turns an error value into null, and a missing rate that quietly became
        // null would write nothing, or zero, where the calculation has to fail. CamundaFeelExpressionEngine reports it as the failure.
        LocalDate on = date == null ? LocalDate.now() : dateOf(date);
        Money converted = ExchangeRateScope.current().convert(new Money(amount.value().bigDecimal(), from.value()), to.value(), on, UnitCatalog.standard());
        return new ValNumber(scala.math.BigDecimal.javaBigDecimal2bigDecimal(converted.amount()));
    }

    /** A FEEL date or date and time, or ISO text; anything else is an INVALID_RATE for the caller to report. */
    private static LocalDate dateOf(Val value) {
        try {
            return switch (value) {
                case ValDate date -> date.value();
                case ValDateTime dateTime -> dateTime.value().toLocalDate();
                case ValLocalDateTime dateTime -> dateTime.value().toLocalDate();
                case ValString text -> LocalDate.parse(text.value().length() > 10 ? text.value().substring(0, 10) : text.value());
                default -> throw new FundamentalTypeException(FundamentalTypeException.Reason.INVALID_RATE, "expected a date");
            };
        } catch (DateTimeParseException e) {
            throw new FundamentalTypeException(FundamentalTypeException.Reason.INVALID_RATE, "expected an ISO date, e.g. 2026-07-01", e);
        }
    }

    private static Val apply(String name, BiFunction<List<Val>, Integer, List<Val>> function, List<Val> arguments) {
        if (!(arguments.get(0) instanceof ValList list) || !(arguments.get(1) instanceof ValNumber count)) {
            return new ValError("%s(list, n) expects a list of numbers and a number".formatted(name));
        }
        List<Val> values = new ArrayList<>(CollectionConverters.asJava(list.itemsAsSeq()));
        if (!values.stream().allMatch(ValNumber.class::isInstance)) {
            return new ValError("%s(list, n) expects a list of numbers".formatted(name));
        }
        List<Val> result = function.apply(values, Math.max(0, count.value().intValue()));
        return new ValList(CollectionConverters.asScala(result).toSeq());
    }

    private static List<Val> sorted(List<Val> values, Comparator<Val> order) {
        List<Val> sorted = new ArrayList<>(values);
        sorted.sort(order);
        return sorted;
    }
}
