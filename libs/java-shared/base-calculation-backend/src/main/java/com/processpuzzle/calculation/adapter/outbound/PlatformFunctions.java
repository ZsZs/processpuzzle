package com.processpuzzle.calculation.adapter.outbound;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import org.camunda.feel.context.JavaFunction;
import org.camunda.feel.context.JavaFunctionProvider;
import org.camunda.feel.syntaxtree.Val;
import org.camunda.feel.syntaxtree.ValError;
import org.camunda.feel.syntaxtree.ValList;
import org.camunda.feel.syntaxtree.ValNumber;
import scala.jdk.javaapi.CollectionConverters;

/**
 * The platform's FEEL functions over lists of numbers, for what FEEL's built-ins leave awkward:
 * <ul>
 *   <li>{@code drop worst(list, n)} — the list without its {@code n} highest values, the worst under a low-point
 *       system;</li>
 *   <li>{@code drop best(list, n)} — the list without its {@code n} lowest values;</li>
 *   <li>{@code top(list, n)} — the {@code n} highest values, highest first.</li>
 * </ul>
 * Grouping and ranking with ties are decision types of their own ({@code aggregate}, {@code rank}), not functions.
 */
class PlatformFunctions extends JavaFunctionProvider {

    private static final Comparator<Val> NUMERIC = Comparator.comparing(value -> ((ValNumber) value).value().bigDecimal());

    private static final Map<String, BiFunction<List<Val>, Integer, List<Val>>> FUNCTIONS = Map.of(
            "drop worst", (values, n) -> sorted(values, NUMERIC).subList(0, Math.max(0, values.size() - n)),
            "drop best", (values, n) -> sorted(values, NUMERIC).subList(Math.min(n, values.size()), values.size()),
            "top", (values, n) -> sorted(values, NUMERIC.reversed()).subList(0, Math.min(n, values.size())));

    @Override
    public Optional<JavaFunction> resolveFunction(String name) {
        BiFunction<List<Val>, Integer, List<Val>> function = FUNCTIONS.get(name);
        return function == null ? Optional.empty()
                : Optional.of(new JavaFunction(List.of("list", "n"), arguments -> apply(name, function, arguments)));
    }

    @Override
    public Collection<String> getFunctionNames() {
        return FUNCTIONS.keySet();
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
