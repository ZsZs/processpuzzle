package com.processpuzzle.calculation.usecase;

import com.processpuzzle.calculation.port.EntityRecord;
import java.util.List;
import java.util.Map;

/**
 * What a calculation computes over: the parameters as the engine sees them — an entity parameter as its typed
 * payload with its {@code id} — every input's rows, and the objects the entity parameters were read as, whose
 * versions a single-object output writes against.
 *
 * @param supplied the parameter values as given — ids for entity parameters — which a run records
 */
public record ResolvedInputs(Map<String, Object> parameters, Map<String, List<Map<String, Object>>> inputs,
                             Map<String, EntityRecord> objects, Map<String, Object> supplied) {
}
