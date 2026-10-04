package com.processpuzzle.baseentity.definition.adapters.outbound;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Moves the retired boolean {@code base_entity_attribute.is_multi_valued} onto {@code multiplicity}.
 *
 * <p>The schema is maintained by Hibernate's {@code ddl-auto: update}, which adds columns but never
 * drops them. The old column is {@code boolean not null} without a default, so once the entity stops
 * mapping it every insert into the table would fail. This copies {@code true} over as {@code 0..n}
 * (false stays absent: a single value) and drops the column.
 *
 * <p>Ordered ahead of {@code DefaultEntityLoader} ({@code @Order(10)}), whose inserts would otherwise
 * hit the old column. Idempotent — a database without the column is left alone — and, like the loader,
 * it never fails startup: a problem is logged and the application comes up regardless.
 */
@Component
public class LegacyMultiValuedMigrator {

    private static final Logger LOG = LoggerFactory.getLogger(LegacyMultiValuedMigrator.class);
    static final String TABLE = "base_entity_attribute";
    static final String LEGACY_COLUMN = "is_multi_valued";

    private final JdbcTemplate jdbc;

    public LegacyMultiValuedMigrator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Order(5)
    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        try {
            if (!legacyColumnExists()) {
                return;
            }
            int migrated = jdbc.update("UPDATE " + TABLE + " SET multiplicity = 'ZERO_TO_N' WHERE "
                + LEGACY_COLUMN + " = TRUE AND multiplicity IS NULL");
            jdbc.execute("ALTER TABLE " + TABLE + " DROP COLUMN " + LEGACY_COLUMN);
            LOG.info("Migrated {} multi-valued attribute(s) to multiplicity 0..n and dropped {}.{}.",
                migrated, TABLE, LEGACY_COLUMN);
        } catch (RuntimeException e) {
            LOG.warn("Failed to migrate {}.{} onto multiplicity; inserts into {} may fail until it is dropped.",
                TABLE, LEGACY_COLUMN, TABLE, e);
        }
    }

    /** Metadata lookup in both letter cases: PostgreSQL folds identifiers to lower case, H2 to upper. */
    boolean legacyColumnExists() {
        Boolean exists = jdbc.execute((ConnectionCallback<Boolean>) connection -> {
            DatabaseMetaData metaData = connection.getMetaData();
            for (String table : new String[] {TABLE, TABLE.toUpperCase()}) {
                try (ResultSet columns = metaData.getColumns(connection.getCatalog(), connection.getSchema(), table, null)) {
                    while (columns.next()) {
                        if (LEGACY_COLUMN.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                            return true;
                        }
                    }
                }
            }
            return false;
        });
        return Boolean.TRUE.equals(exists);
    }
}
