package com.processpuzzle.core.definition;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Moves an existing definition table onto {@link Channel}s, once: every row becomes the
 * {@link Channel#DRAFT}, gets a {@link Channel#RELEASED} copy, and the primary key widens to include
 * {@code channel}. An organization therefore runs exactly as before — what it was editing is now both
 * its draft and its release.
 *
 * <p>Needed because the schema is maintained by Hibernate's {@code ddl-auto: update}, which adds the
 * {@code channel} column but never changes a primary key, so the released copy of a row would collide
 * with its draft. The entity maps {@code channel} with a database default of {@code 'DRAFT'}, which is
 * what lets Hibernate add the column as {@code not null} to a table that already has rows.
 *
 * <p>Idempotent: a table whose primary key already includes {@code channel} — every table Hibernate
 * created after the change — is left alone. All statements run in one transaction, so on a database
 * with transactional DDL (PostgreSQL) a failure leaves the table as it was, to be retried on the next
 * start.
 */
public class ChannelBackfill {

    static final String CHANNEL_COLUMN = "channel";
    private static final String ALTER_TABLE = "ALTER TABLE ";
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,62}");
    private static final Logger LOG = LoggerFactory.getLogger(ChannelBackfill.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public ChannelBackfill(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * @param table      the definition table, in lower case
     * @param keyColumns the columns of its primary key before the change, without {@code channel}
     * @return whether the table was migrated; {@code false} when there was nothing to do
     */
    public boolean migrate(String table, List<String> keyColumns) {
        Optional<TableShape> shape = shapeOf(table);
        if (shape.isEmpty() || shape.get().primaryKeyColumns().contains(CHANNEL_COLUMN)) {
            return false;
        }
        TableShape found = shape.get();
        // Every name is checked before the first statement runs: some databases commit DDL at once.
        String name = identifier(table);
        String primaryKey = found.primaryKeyName() == null ? null : identifier(found.primaryKeyName());
        List<String> widened = new ArrayList<>(keyColumns);
        widened.add(CHANNEL_COLUMN);
        String widenedKey = identifiers(widened);
        String copied = identifiers(found.columns().stream().filter(column -> !CHANNEL_COLUMN.equals(column)).toList());
        Boolean done = transaction.execute(status -> {
            if (!found.columns().contains(CHANNEL_COLUMN)) {
                run(ALTER_TABLE + name + " ADD COLUMN " + CHANNEL_COLUMN + " VARCHAR(16) DEFAULT 'DRAFT' NOT NULL");
            }
            run("UPDATE " + name + " SET " + CHANNEL_COLUMN + " = 'DRAFT' WHERE " + CHANNEL_COLUMN + " IS NULL");
            if (primaryKey != null) {
                run(ALTER_TABLE + name + " DROP CONSTRAINT " + primaryKey);
            }
            run(ALTER_TABLE + name + " ADD PRIMARY KEY (" + widenedKey + ")");
            int released = run("INSERT INTO " + name + " (" + copied + ", " + CHANNEL_COLUMN + ") SELECT "
                    + copied + ", 'RELEASED' FROM " + name + " WHERE " + CHANNEL_COLUMN + " = 'DRAFT'");
            LOG.info("Moved {} onto channels: its {} definition(s) are now drafts, each with a released copy.", table, released);
            return true;
        });
        return Boolean.TRUE.equals(done);
    }

    /**
     * Runs one statement of the migration. The statements are assembled from identifiers rather than bound
     * parameters, because DDL takes no parameters; every identifier comes from the calling feature's code or
     * the database's own metadata, never from a request, and {@link #identifier} has checked each one.
     */
    private int run(String sql) {
        return jdbc.update(sql); // NOSONAR java:S2077 - identifiers only, each checked by identifier()
    }

    /** A table, column or constraint name, refused unless it is a plain SQL identifier. */
    static String identifier(String name) {
        if (name == null || !IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Not a plain SQL identifier: '" + name + "'.");
        }
        return name;
    }

    private static String identifiers(List<String> names) {
        return names.stream().map(ChannelBackfill::identifier).collect(Collectors.joining(", "));
    }

    /** The table's columns and primary key, in lower case; empty when the table does not exist. */
    Optional<TableShape> shapeOf(String table) {
        return Optional.ofNullable(jdbc.execute((ConnectionCallback<TableShape>) connection -> {
            DatabaseMetaData metaData = connection.getMetaData();
            // PostgreSQL folds unquoted identifiers to lower case, H2 to upper.
            for (String name : new String[] {table, table.toUpperCase(Locale.ROOT)}) {
                List<String> columns = columnsOf(metaData, connection.getCatalog(), connection.getSchema(), name);
                if (!columns.isEmpty()) {
                    return primaryKeyOf(metaData, connection.getCatalog(), connection.getSchema(), name, columns);
                }
            }
            return null;
        }));
    }

    private static List<String> columnsOf(DatabaseMetaData metaData, String catalog, String schema, String table)
            throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet rows = metaData.getColumns(catalog, schema, table, null)) {
            while (rows.next()) {
                columns.add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return columns;
    }

    private static TableShape primaryKeyOf(DatabaseMetaData metaData, String catalog, String schema, String table,
                                           List<String> columns) throws SQLException {
        List<String> keyColumns = new ArrayList<>();
        String keyName = null;
        try (ResultSet rows = metaData.getPrimaryKeys(catalog, schema, table)) {
            while (rows.next()) {
                keyColumns.add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                keyName = rows.getString("PK_NAME");
            }
        }
        return new TableShape(columns, keyColumns, keyName);
    }

    record TableShape(List<String> columns, List<String> primaryKeyColumns, String primaryKeyName) {
    }
}
