package com.processpuzzle.calculation.usecase;

import com.processpuzzle.calculation.domain.definition.CalculationModel;
import com.processpuzzle.calculation.domain.definition.OutputSpec;
import com.processpuzzle.calculation.engine.EvaluationResult;
import com.processpuzzle.calculation.engine.Values;
import com.processpuzzle.calculation.port.EntityRecord;
import com.processpuzzle.calculation.port.EntityWritePort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Writes a run's output: the mapped values onto the one target object, against the version it was read at, or one
 * object per group, found by its key or created. A mapping source is a decision, a group's attribute, or a
 * parameter — an entity parameter by its object's id.
 */
@Component
public class OutputWriter {

    private final EntityWritePort write;

    public OutputWriter(EntityWritePort write) {
        this.write = write;
    }

    /** @return how many objects were written; none for an {@code onRead} output or none at all */
    public int write(String orgKey, CalculationModel model, ResolvedInputs inputs, EvaluationResult result) {
        OutputSpec output = model.output();
        if (output == null || !output.isStored()) {
            return 0;
        }
        if (!output.isGrouped()) {
            EntityRecord target = inputs.objects().get(targetOf(model));
            Map<String, Object> values = new LinkedHashMap<>();
            output.mapping().forEach((attribute, source) -> values.put(attribute, valueOf(source, Map.of(), result, inputs)));
            write.updateAttributes(orgKey, output.entity(), target.id(), values, target.version());
            return 1;
        }
        String attribute = groupAttributeOf(output);
        List<Map<String, Object>> groups = result.groups().getOrDefault(attribute, List.of());
        List<Map<String, Object>> rows = new ArrayList<>(groups.size());
        for (Map<String, Object> group : groups) {
            Map<String, Object> values = new LinkedHashMap<>();
            output.mapping().forEach((target, source) -> values.put(target, valueOf(source, group, result, inputs)));
            rows.add(values);
        }
        if (!rows.isEmpty()) {
            write.upsertAllByKey(orgKey, output.entity(), output.key(), rows);
        }
        return rows.size();
    }

    /** The entity parameter a single-object output writes — {@code target}, or the one parameter of its entity. */
    public static String targetOf(CalculationModel model) {
        OutputSpec output = model.output();
        if (output.target() != null && !output.target().isBlank()) {
            return output.target();
        }
        List<String> candidates = model.parametersOfEntity(output.entity());
        return candidates.isEmpty() ? null : candidates.getFirst();
    }

    static String groupAttributeOf(OutputSpec output) {
        int dot = output.scope().indexOf('.');
        return dot < 0 ? output.scope() : output.scope().substring(dot + 1);
    }

    private static Object valueOf(String source, Map<String, Object> group, EvaluationResult result, ResolvedInputs inputs) {
        if (group.containsKey(source)) {
            return Values.plain(group.get(source));
        }
        if (result.values().containsKey(source)) {
            return Values.plain(result.values().get(source));
        }
        EntityRecord object = inputs.objects().get(source);
        return object != null ? object.id() : Values.plain(inputs.parameters().get(source));
    }
}
