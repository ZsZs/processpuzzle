package com.processpuzzle.core.persistence;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

/**
 * Widens the columns of Spring Modulith's event publication registry that hold whole events.
 *
 * <p>{@code spring-modulith-events-jpa} maps {@code serialized_event} with a bare {@code @Column}, so Hibernate's
 * {@code ddl-auto} creates it as {@code varchar(255)}. Every save of an entity object publishes an event carrying the
 * object's payload, so an object of more than a handful of fields no longer fits, and its save fails with "value too
 * long for type character varying(255)" (stage, 2026-10-13). {@code listener_id} names a listener method with its
 * parameter types, which can outgrow 255 as well. Both become {@code text}.
 *
 * <p>Checked on every start, because {@code ddl-auto} creates the narrow column on a fresh database and never alters
 * an existing one. A database without the registry, or one already widened, is left alone; like the definition
 * backfills, this never fails startup.
 */
@Component
public class EventPublicationColumns {

    static final String TABLE = "event_publication";
    static final List<String> WIDE_COLUMNS = List.of("serialized_event", "listener_id");
    /** Below this, a column is the narrow one {@code ddl-auto} made; text and unbounded varchar report none, or far more. */
    private static final long MAX_NARROW_WIDTH = 1_000_000;
    private static final Logger LOG = LoggerFactory.getLogger(EventPublicationColumns.class);

    private final JdbcTemplate jdbc;

    public EventPublicationColumns(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Order(1)
    @EventListener(ApplicationReadyEvent.class)
    public void widen() {
        try {
            widenOnce();
        } catch (RuntimeException e) {
            LOG.warn("Failed to widen the columns of {}; events larger than their width cannot be published.", TABLE, e);
        }
    }

    /** @return the columns widened; empty when there was nothing to widen */
    public List<String> widenOnce() {
        // A column's width: none for text, a number for varchar. Absent from the map when the column is.
        Map<String, Long> widths = new HashMap<>();
        jdbc.query("SELECT column_name, character_maximum_length FROM information_schema.columns WHERE LOWER(table_name) = ?",
                (RowCallbackHandler) row -> {
                    long width = row.getLong(2);
                    widths.put(row.getString(1).toLowerCase(Locale.ROOT), row.wasNull() ? null : width);
                }, TABLE);
        List<String> narrow = WIDE_COLUMNS.stream()
                .filter(column -> widths.containsKey(column) && isNarrow(widths.get(column)))
                .toList();
        // Constants only, never input: the table and column names are this class's own.
        narrow.forEach(column -> jdbc.execute("ALTER TABLE " + TABLE + " ALTER COLUMN " + column + " TYPE TEXT")); // NOSONAR java:S2077
        if (!narrow.isEmpty()) {
            LOG.info("Widened {} of {} to text, so that a publication is never refused for its size.", narrow, TABLE);
        }
        return narrow;
    }

    /** Narrow enough to refuse an event: a width, and a small one — not text, nor a database's unbounded varchar. */
    private static boolean isNarrow(Long width) {
        return width != null && width < MAX_NARROW_WIDTH;
    }
}
