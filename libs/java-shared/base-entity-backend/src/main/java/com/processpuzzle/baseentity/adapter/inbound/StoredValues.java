package com.processpuzzle.baseentity.adapter.inbound;

import com.processpuzzle.baseentity.definition.domain.ValueKind;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Map;

/**
 * Whether a value an object holds fits an attribute of a given shape — how a release tells the objects a changed
 * attribute would no longer describe from those it still does. Mirrors how payloads are stored: NUMBER and BOOLEAN as
 * native JSON types, DATE and DATE_TIME as ISO-8601 strings, a reference as an id or as the embedded component itself,
 * and a multi-valued attribute as an array of such values.
 */
final class StoredValues {

    private StoredValues() {
    }

    /** No value: absent, {@code null}, a blank string, or an empty array. */
    static boolean isBlank(Object value) {
        return value == null
            || value instanceof String text && text.isBlank()
            || value instanceof Collection<?> values && values.isEmpty();
    }

    /** Whether a value that is not {@linkplain #isBlank blank} fits an attribute of {@code kind}. */
    static boolean fits(Object value, ValueKind kind, boolean multiValued) {
        if (multiValued) {
            return value instanceof Collection<?> values && values.stream().allMatch(element -> fitsOne(element, kind));
        }
        return !(value instanceof Collection<?>) && fitsOne(value, kind);
    }

    private static boolean fitsOne(Object value, ValueKind kind) {
        if (value == null) {
            return true;
        }
        return switch (kind) {
            case TEXT, ENUM -> value instanceof String;
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case DATE -> value instanceof String text && parses(() -> LocalDate.parse(text));
            case DATE_TIME -> value instanceof String text
                && (parses(() -> OffsetDateTime.parse(text)) || parses(() -> LocalDateTime.parse(text)));
            case REFERENCE -> value instanceof String || value instanceof Map<?, ?>;
        };
    }

    private static boolean parses(Runnable parse) {
        try {
            parse.run();
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
