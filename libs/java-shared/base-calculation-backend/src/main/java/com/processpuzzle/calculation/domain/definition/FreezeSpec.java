package com.processpuzzle.calculation.domain.definition;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Map;

/**
 * When a subject's result stops changing: {@code onCompletion} freezes every completed run at once — a one-time
 * result — and {@code { state: { entity, to } }} freezes the subject's result when it enters that state.
 *
 * @param unknown what the source said when it is neither, kept for the validator to report
 */
public record FreezeSpec(boolean onCompletion, StateRef state, String unknown) {

    public static final String ON_COMPLETION = "onCompletion";

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static FreezeSpec of(Object value) {
        if (value instanceof String text) {
            return ON_COMPLETION.equals(text) ? new FreezeSpec(true, null, null) : new FreezeSpec(false, null, text);
        }
        if (value instanceof Map<?, ?> map && map.size() == 1 && map.get("state") instanceof Map<?, ?> state) {
            return new FreezeSpec(false, new StateRef(text(state.get("entity")), text(state.get("to"))), null);
        }
        return new FreezeSpec(false, null, String.valueOf(value));
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
