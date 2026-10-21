package com.processpuzzle.calculation.usecase;

import com.processpuzzle.calculation.domain.definition.CalculationModel;
import com.processpuzzle.calculation.domain.definition.InputSpec;
import com.processpuzzle.calculation.domain.definition.ParameterSpec;
import com.processpuzzle.calculation.engine.CalculationAnalyzer;
import com.processpuzzle.calculation.port.EntityQueryPort;
import com.processpuzzle.calculation.port.EntityRecord;
import com.processpuzzle.calculation.usecase.exception.InvalidParametersException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Reads what a calculation computes over. An entity parameter's object is read by id; a query input's filter gets
 * its {@code ${parameter}} placeholders replaced — an entity parameter by its id, {@code ${parameter.attribute}} by
 * that attribute of its object, quoted for RSQL where needed; an embedded input is the list at its path in a
 * parameter's object. An entity parameter with an unsaved payload reads that instead of the stored object, so a
 * form can ask what a calculation would produce from what is on screen. Every row carries its object's {@code id}, an embedded row its position as {@code index}.
 */
@Component
public class InputResolver {

    private static final Pattern RSQL_PLAIN = Pattern.compile("[\\w.\\-:]+");

    private final EntityQueryPort query;
    private final TypedValues typedValues;

    public InputResolver(EntityQueryPort query, TypedValues typedValues) {
        this.query = query;
        this.typedValues = typedValues;
    }

    /**
     * @throws InvalidParametersException when a parameter has no value, or an entity parameter's object does not
     *                                    exist
     */
    public ResolvedInputs resolve(String orgKey, CalculationModel model, Map<String, Object> values) {
        return resolve(orgKey, model, values, null);
    }

    /**
     * @param unsaved payloads by entity parameter name, read instead of the stored objects; an unsaved object
     *                without an id substitutes nothing into query inputs
     * @throws InvalidParametersException when a parameter has no value, or an entity parameter's object does not
     *                                    exist
     */
    public ResolvedInputs resolve(String orgKey, CalculationModel model, Map<String, Object> values,
                                  Map<String, Map<String, Object>> unsaved) {
        Map<String, Object> given = values == null ? Map.of() : values;
        TypedValues.Typer typer = typedValues.typer(orgKey);
        Map<String, Object> parameters = new LinkedHashMap<>();
        Map<String, EntityRecord> objects = new LinkedHashMap<>();
        Map<String, Object> supplied = new LinkedHashMap<>();
        model.parameters().forEach((name, parameter) -> {
            Object value = given.get(name) != null ? given.get(name) : parameter.defaultValue();
            Map<String, Object> draft = draftOf(name, parameter, unsaved);
            if (value == null && draft == null) {
                throw new InvalidParametersException("parameter %s needs a value".formatted(name));
            }
            if (value != null) {
                supplied.put(name, value);
            }
            if (!parameter.isEntity()) {
                parameters.put(name, TypedValues.ofParameterType(parameter.type(), value));
                return;
            }
            EntityRecord object = draft != null ? unsavedObject(draft, value) : read(orgKey, name, parameter, value);
            objects.put(name, object);
            parameters.put(name, typedPayload(typer, parameter, object));
        });

        Map<String, List<Map<String, Object>>> inputs = new LinkedHashMap<>();
        model.inputs().forEach((name, input) -> inputs.put(name, input.isEmbedded()
                ? embedded(typer, input, objects.get(input.from()))
                : queried(orgKey, typer, input, supplied, objects)));
        return new ResolvedInputs(parameters, inputs, objects, supplied);
    }

    private static Map<String, Object> typedPayload(TypedValues.Typer typer, ParameterSpec parameter, EntityRecord object) {
        Map<String, Object> payload = typer.payload(parameter.entity(), object.payload());
        payload.put("id", object.id());
        return payload;
    }

    private static Map<String, Object> draftOf(String name, ParameterSpec parameter, Map<String, Map<String, Object>> unsaved) {
        return parameter.isEntity() && unsaved != null ? unsaved.get(name) : null;
    }

    private static EntityRecord unsavedObject(Map<String, Object> draft, Object value) {
        Object id = draft.get("id");
        if (id == null) {
            id = idOf(value);
        }
        return new EntityRecord(id == null ? null : id.toString(), 0, draft);
    }

    private static Object idOf(Object value) {
        return value instanceof Map<?, ?> map ? map.get("id") : value;
    }

    private EntityRecord read(String orgKey, String name, ParameterSpec parameter, Object value) {
        String id = value instanceof Map<?, ?> map && map.get("id") != null ? map.get("id").toString() : value.toString();
        return query.find(orgKey, parameter.entity(), id).orElseThrow(() -> new InvalidParametersException(
                "parameter '%s': there is no %s '%s'".formatted(name, parameter.entity(), id)));
    }

    private List<Map<String, Object>> embedded(TypedValues.Typer typer, InputSpec input, EntityRecord owner) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (owner != null && owner.payload().get(input.path()) instanceof List<?> items) {
            int index = 0;
            for (Object item : items) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> stored = new LinkedHashMap<>();
                    map.forEach((key, value) -> stored.put(String.valueOf(key), value));
                    Map<String, Object> row = typer.payload(input.entity(), stored);
                    row.putIfAbsent("index", java.math.BigDecimal.valueOf(index));
                    rows.add(row);
                }
                index++;
            }
        }
        return rows;
    }

    private List<Map<String, Object>> queried(String orgKey, TypedValues.Typer typer, InputSpec input,
                                              Map<String, Object> supplied, Map<String, EntityRecord> objects) {
        String rsql = substitute(input.where(), supplied, objects);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EntityRecord object : query.findWhere(orgKey, input.entity(), rsql)) {
            Map<String, Object> row = typer.payload(input.entity(), object.payload());
            row.put("id", object.id());
            rows.add(row);
        }
        return rows;
    }

    static String substitute(String where, Map<String, Object> supplied, Map<String, EntityRecord> objects) {
        if (where == null || where.isBlank()) {
            return null;
        }
        Matcher matcher = CalculationAnalyzer.PLACEHOLDER.matcher(where);
        StringBuilder rsql = new StringBuilder();
        while (matcher.find()) {
            String[] path = matcher.group(1).trim().split("\\.", 2);
            Object value;
            if (path.length == 1) {
                EntityRecord object = objects.get(path[0]);
                value = object != null ? object.id() : supplied.get(path[0]);
            } else {
                EntityRecord object = objects.get(path[0]);
                value = object == null ? null : object.payload().get(path[1]);
            }
            matcher.appendReplacement(rsql, Matcher.quoteReplacement(literal(value)));
        }
        matcher.appendTail(rsql);
        return rsql.toString();
    }

    private static String literal(Object value) {
        String text = value == null ? "" : value.toString();
        return RSQL_PLAIN.matcher(text).matches() ? text : '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
