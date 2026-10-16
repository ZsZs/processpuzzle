package com.processpuzzle.baseentity.instances.usecases.inbound;

import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.common.ValidationException.Violation;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Keeps every COMMENTS attribute append-only across an update: the stored history has to be an unchanged prefix of
 * the submitted list, so a save may add comments at the end and do nothing else. Editing, removing, reordering or
 * truncating a stored comment is rejected with a 422.
 *
 * <p>An update that leaves the attribute out keeps the stored history rather than erasing it — a client that does
 * not know about comments must not be able to delete them by omission.
 *
 * <p>Compared structurally rather than with {@code equals}: the stored payload and the submitted one are parsed by
 * different paths, and the same JSON number may arrive as an {@code Integer} on one side and a {@code Long} or a
 * {@code Double} on the other.
 */
final class CommentHistoryGuard {

    static final String APPEND_ONLY = "comments are append-only";

    private CommentHistoryGuard() {
    }

    /** The submitted payload with omitted comment histories restored; throws when a stored comment was changed. */
    static Map<String, Object> guard(EntityDefinitionView definition, Map<String, Object> storedPayload, Map<String, Object> payload) {
        Map<String, Object> stored = storedPayload == null ? Map.of() : storedPayload;
        Map<String, Object> effective = payload;
        List<Violation> violations = new ArrayList<>();

        for (EntityAttributeView attribute : definition.attributes()) {
            if (!attribute.comments() || !(stored.get(attribute.code()) instanceof List<?> history) || history.isEmpty()) {
                continue;
            }
            if (!payload.containsKey(attribute.code())) {
                if (effective == payload) {
                    effective = new HashMap<>(payload);
                }
                effective.put(attribute.code(), history);
            } else if (!startsWith(payload.get(attribute.code()), history)) {
                violations.add(new Violation(attribute.code(), APPEND_ONLY));
            }
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
        return effective;
    }

    private static boolean startsWith(Object submitted, List<?> history) {
        if (!(submitted instanceof List<?> comments) || comments.size() < history.size()) {
            return false;
        }
        for (int i = 0; i < history.size(); i++) {
            if (!sameValue(history.get(i), comments.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameValue(Object left, Object right) {
        if (left instanceof Number a && right instanceof Number b) {
            return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
        }
        if (left instanceof Map<?, ?> a && right instanceof Map<?, ?> b) {
            return a.size() == b.size() && a.entrySet().stream().allMatch(entry -> b.containsKey(entry.getKey()) && sameValue(entry.getValue(), b.get(entry.getKey())));
        }
        if (left instanceof List<?> a && right instanceof List<?> b) {
            if (a.size() != b.size()) {
                return false;
            }
            for (int i = 0; i < a.size(); i++) {
                if (!sameValue(a.get(i), b.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.equals(left, right);
    }
}
