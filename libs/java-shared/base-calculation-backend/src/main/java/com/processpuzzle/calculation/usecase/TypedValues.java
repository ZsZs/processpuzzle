package com.processpuzzle.calculation.usecase;

import com.processpuzzle.calculation.engine.Values;
import com.processpuzzle.calculation.port.AttributeType;
import com.processpuzzle.calculation.port.EntityAttributeTypePort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Turns stored payload values into what FEEL computes with: every number a {@link BigDecimal}, and the text of a
 * {@code DATE} or {@code DATE_TIME} attribute a {@code java.time} value — a date-time with an offset as a
 * {@link java.time.ZonedDateTime}, one without as a {@link LocalDateTime}. A value that does not parse as its type
 * is passed as stored, for FEEL to report where it is used.
 */
public class TypedValues {

    private final EntityAttributeTypePort types;

    public TypedValues(EntityAttributeTypePort types) {
        this.types = types;
    }

    /** One resolution's typer: it asks for each attribute's type once. */
    public Typer typer(String orgKey) {
        return new Typer(orgKey);
    }

    public final class Typer {

        private final String orgKey;
        private final Map<String, Optional<AttributeType>> cache = new HashMap<>();

        private Typer(String orgKey) {
            this.orgKey = orgKey;
        }

        /** A typed copy of {@code payload}; {@code entity} may be null, which types numbers only. */
        public Map<String, Object> payload(String entity, Map<String, Object> payload) {
            Map<String, Object> typed = new LinkedHashMap<>();
            payload.forEach((attribute, value) -> typed.put(attribute, value(entity, attribute, value)));
            return typed;
        }

        public Object value(String entity, String attribute, Object value) {
            if (value == null) {
                return null;
            }
            BigDecimal number = Values.number(value);
            if (number != null) {
                return number;
            }
            if (!(value instanceof String text) || entity == null) {
                return value;
            }
            AttributeType type = cache.computeIfAbsent(entity + "\u0000" + attribute,
                    key -> types.typeOf(orgKey, entity, attribute)).orElse(AttributeType.OTHER);
            return parse(type, text);
        }
    }

    /** A plain parameter's value as its declared type. */
    public static Object ofParameterType(String type, Object value) {
        if (value == null || !(value instanceof String text)) {
            BigDecimal number = Values.number(value);
            return number != null ? number : value;
        }
        return switch (type) {
            case "number" -> parse(AttributeType.NUMBER, text);
            case "boolean" -> parse(AttributeType.BOOLEAN, text);
            case "date" -> parse(AttributeType.DATE, text);
            case "date-time" -> parse(AttributeType.DATE_TIME, text);
            default -> text;
        };
    }

    private static Object parse(AttributeType type, String text) {
        try {
            return switch (type) {
                case NUMBER -> new BigDecimal(text.trim());
                case BOOLEAN -> bool(text);
                case DATE -> LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
                case DATE_TIME -> dateTime(text);
                default -> text;
            };
        } catch (NumberFormatException | DateTimeParseException e) {
            return text;
        }
    }

    private static Object bool(String text) {
        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        return "false".equalsIgnoreCase(text) ? Boolean.FALSE : text;
    }

    private static Object dateTime(String text) {
        try {
            return OffsetDateTime.parse(text).toZonedDateTime();
        } catch (DateTimeParseException e) {
            return LocalDateTime.parse(text);
        }
    }
}
