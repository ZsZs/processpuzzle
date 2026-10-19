package com.processpuzzle.calculation.adapter.outbound;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.camunda.feel.syntaxtree.Val;
import org.camunda.feel.syntaxtree.ValContext;
import org.camunda.feel.syntaxtree.ValList;
import org.camunda.feel.syntaxtree.ValNumber;
import org.camunda.feel.valuemapper.JavaCustomValueMapper;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Hands FEEL values to Java as the engine expects them: numbers as {@link java.math.BigDecimal} — feel-scala's own
 * Java mapper turns them into {@code double}, which loses the cents of a price — lists as {@link List} and contexts
 * as {@link Map}. Everything else, {@code java.time} values included, is left to the default mapper.
 */
class PlatformValueMapper extends JavaCustomValueMapper {

    @Override
    public Optional<Val> toValue(Object value, Function<Object, Val> innerValueMapper) {
        return Optional.empty();
    }

    @Override
    public Optional<Object> unpackValue(Val value, Function<Val, Object> innerValueMapper) {
        return switch (value) {
            case ValNumber number -> Optional.of(number.value().bigDecimal());
            case ValList list -> {
                List<Object> items = new ArrayList<>();
                CollectionConverters.asJava(list.itemsAsSeq()).forEach(item -> items.add(innerValueMapper.apply(item)));
                yield Optional.of(items);
            }
            case ValContext context -> {
                Map<String, Object> entries = new LinkedHashMap<>();
                CollectionConverters.asJava(context.context().variableProvider().getVariables()).forEach((key, entry) ->
                        entries.put(key, entry instanceof Val packed ? innerValueMapper.apply(packed) : entry));
                yield Optional.of(entries);
            }
            default -> Optional.empty();
        };
    }

    @Override
    public int priority() {
        return 10;
    }
}
