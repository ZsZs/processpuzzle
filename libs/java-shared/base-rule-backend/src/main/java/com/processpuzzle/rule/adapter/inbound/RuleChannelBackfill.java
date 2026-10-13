package com.processpuzzle.rule.adapter.inbound;

import com.processpuzzle.core.definition.ChannelBackfill;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Moves {@code rule_definitions} onto channels once, so that every rule an organization had before releases existed is
 * both its draft and its release; see {@link ChannelBackfill}.
 *
 * <p>A rule's fields live in {@code rule_definition_fields}, which joins on the rule's key and so needs the channel
 * too. Two things stand in the way that {@code ChannelBackfill} alone cannot clear:
 * <ul>
 *   <li>the fields' foreign key onto {@code rule_definitions}' primary key, which a database refuses to drop that key
 *       under — so the old foreign key, the one without {@code channel}, goes first; Hibernate's {@code ddl-auto}
 *       creates the one with {@code channel} on the next start;</li>
 *   <li>the fields' own {@code channel} column, which {@code ddl-auto} cannot add as {@code not null} to a table that
 *       already has rows — so it is added here, defaulting to {@code DRAFT}, when it is missing.</li>
 * </ul>
 * Then the rules are widened and copied, and the fields of every released copy copied after them. All of it runs in
 * one transaction, so a failure leaves both tables as they were, to be retried on the next start.
 *
 * <p><b>The primary key's column order matters</b>, though a primary key is a set. Hibernate writes the fields'
 * foreign key as {@code (channel, rule_id, org_key) REFERENCES rule_definitions}, naming no columns on the referenced
 * side, so the database pairs them with the primary key's columns by position. Widening appends {@code channel} to
 * the old key, {@code (org_key, id)}, which pairs {@code channel} with {@code org_key} and makes every existing row
 * violate the new foreign key: it is never created. So the key is rebuilt in Hibernate's order,
 * {@link #KEY_IN_HIBERNATE_ORDER} — also on a database migrated before this was known, which is why it is checked on
 * every start. It can only be rebuilt while no foreign key depends on it, which is exactly the state it leaves behind.
 *
 * <p>Ordered ahead of {@code SampleRuleLoader}, whose inserts need the widened key. Never fails startup.
 */
@Component
public class RuleChannelBackfill {

    static final String RULES = "rule_definitions";
    static final String FIELDS = "rule_definition_fields";
    private static final String CHANNEL = "channel";
    private static final String ORG_KEY = "org_key";
    /** The rules' key before channels. */
    private static final List<String> KEY_BEFORE_CHANNELS = List.of(ORG_KEY, "id");
    /** The order Hibernate lists {@code RuleDefinitionKey}'s columns in, which its foreign keys rely on. */
    static final List<String> KEY_IN_HIBERNATE_ORDER = List.of(CHANNEL, "id", ORG_KEY);
    private static final String KEY_COLUMNS = String.join(", ", KEY_IN_HIBERNATE_ORDER);
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_]\\w{0,62}");
    private static final Logger LOG = LoggerFactory.getLogger(RuleChannelBackfill.class);

    private final JdbcTemplate jdbc;
    private final ChannelBackfill backfill;
    private final TransactionTemplate transaction;

    public RuleChannelBackfill(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.backfill = new ChannelBackfill(jdbc, transactionManager);
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Order(5)
    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        try {
            migrateOnce();
        } catch (RuntimeException e) {
            LOG.warn("Failed to move {} onto channels; releasing a rule will fail until it is.", RULES, e);
        }
    }

    /** @return whether the tables were changed; {@code false} when they were migrated already, or never needed it */
    public boolean migrateOnce() {
        if (columnsOf(FIELDS).isEmpty()) {
            return Boolean.TRUE.equals(transaction.execute(status -> backfill.migrate(RULES, KEY_BEFORE_CHANNELS)));
        }
        return Boolean.TRUE.equals(transaction.execute(status -> {
            oldForeignKeys().forEach(name -> jdbc.execute("ALTER TABLE " + FIELDS + " DROP CONSTRAINT " + identifier(name))); // NOSONAR java:S2077 - identifier() checked
            if (!columnsOf(FIELDS).contains(CHANNEL)) {
                jdbc.execute("ALTER TABLE " + FIELDS + " ADD COLUMN channel VARCHAR(16) DEFAULT 'DRAFT' NOT NULL"); // NOSONAR java:S2077 - constants only
            }
            boolean migrated = backfill.migrate(RULES, KEY_BEFORE_CHANNELS);
            if (migrated) {
                int copied = jdbc.update("INSERT INTO " + FIELDS + " (org_key, rule_id, field_name, channel) " // NOSONAR java:S2077 - constants only
                        + "SELECT org_key, rule_id, field_name, 'RELEASED' FROM " + FIELDS + " WHERE channel = 'DRAFT'");
                LOG.info("Copied {} rule field(s) onto the released rules.", copied);
            }
            return alignPrimaryKey() || migrated;
        }));
    }

    /**
     * Rebuilds the rules' widened primary key in {@link #KEY_IN_HIBERNATE_ORDER}, so that Hibernate's foreign key from
     * the fields can be created; see the class comment.
     *
     * @return whether it was rebuilt; {@code false} when it is in that order already, not widened yet, or held by a
     *         foreign key — which Hibernate only manages to create once the order is right
     */
    boolean alignPrimaryKey() {
        List<Map.Entry<String, String>> key = primaryKeyOf(RULES);
        List<String> columns = key.stream().map(Map.Entry::getValue).toList();
        if (columns.isEmpty() || !columns.contains(CHANNEL) || columns.equals(KEY_IN_HIBERNATE_ORDER)
                || !foreignKeysOf(FIELDS).isEmpty()) {
            return false;
        }
        jdbc.execute("ALTER TABLE " + RULES + " DROP CONSTRAINT " + identifier(key.getFirst().getKey())); // NOSONAR java:S2077 - identifier() checked
        jdbc.execute("ALTER TABLE " + RULES + " ADD PRIMARY KEY (" + KEY_COLUMNS + ")"); // NOSONAR java:S2077 - constants only
        LOG.info("Rebuilt the primary key of {} as ({}), the order the foreign key of {} refers to it in.", RULES,
                KEY_COLUMNS, FIELDS);
        return true;
    }

    /** The primary key's columns in key order, each with the constraint's name; empty when there is none. */
    private List<Map.Entry<String, String>> primaryKeyOf(String table) {
        return jdbc.query(
                "SELECT tc.constraint_name, k.column_name FROM information_schema.table_constraints tc "
                        + "JOIN information_schema.key_column_usage k ON k.constraint_name = tc.constraint_name "
                        + "AND k.table_name = tc.table_name "
                        + "WHERE LOWER(tc.table_name) = ? AND tc.constraint_type = 'PRIMARY KEY' ORDER BY k.ordinal_position",
                (row, index) -> Map.entry(row.getString(1), row.getString(2).toLowerCase(Locale.ROOT)), table);
    }

    /** The foreign keys of the fields table that do not include {@code channel}: the ones from before channels. */
    List<String> oldForeignKeys() {
        return foreignKeysOf(FIELDS).entrySet().stream()
                .filter(entry -> !entry.getValue().contains(CHANNEL))
                .map(Map.Entry::getKey)
                .toList();
    }

    /** The table's foreign keys, by name, with their columns. */
    private Map<String, Set<String>> foreignKeysOf(String table) {
        return jdbc.query(
                        "SELECT tc.constraint_name, k.column_name FROM information_schema.table_constraints tc "
                                + "JOIN information_schema.key_column_usage k ON k.constraint_name = tc.constraint_name "
                                + "AND k.table_name = tc.table_name "
                                + "WHERE LOWER(tc.table_name) = ? AND tc.constraint_type = 'FOREIGN KEY'",
                        (row, index) -> Map.entry(row.getString(1), row.getString(2).toLowerCase(Locale.ROOT)), table)
                .stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toSet())));
    }

    private Set<String> columnsOf(String table) {
        return jdbc.query("SELECT column_name FROM information_schema.columns WHERE LOWER(table_name) = ?",
                        (row, index) -> row.getString(1).toLowerCase(Locale.ROOT), table)
                .stream().collect(Collectors.toSet());
    }

    private static String identifier(String name) {
        if (!IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Not a plain SQL identifier: '" + name + "'.");
        }
        return name;
    }
}
