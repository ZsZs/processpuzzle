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
 * <p>Ordered ahead of {@code SampleRuleLoader}, whose inserts need the widened key. Never fails startup.
 */
@Component
public class RuleChannelBackfill {

    static final String RULES = "rule_definitions";
    static final String FIELDS = "rule_definition_fields";
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

    /** @return whether the tables were migrated; {@code false} when they were already, or never needed it */
    public boolean migrateOnce() {
        if (columnsOf(FIELDS).isEmpty()) {
            return Boolean.TRUE.equals(transaction.execute(status -> backfill.migrate(RULES, List.of("org_key", "id"))));
        }
        return Boolean.TRUE.equals(transaction.execute(status -> {
            oldForeignKeys().forEach(name -> jdbc.execute("ALTER TABLE " + FIELDS + " DROP CONSTRAINT " + identifier(name))); // NOSONAR java:S2077 - identifier() checked
            if (!columnsOf(FIELDS).contains("channel")) {
                jdbc.execute("ALTER TABLE " + FIELDS + " ADD COLUMN channel VARCHAR(16) DEFAULT 'DRAFT' NOT NULL"); // NOSONAR java:S2077 - constants only
            }
            boolean migrated = backfill.migrate(RULES, List.of("org_key", "id"));
            if (migrated) {
                int copied = jdbc.update("INSERT INTO " + FIELDS + " (org_key, rule_id, field_name, channel) " // NOSONAR java:S2077 - constants only
                        + "SELECT org_key, rule_id, field_name, 'RELEASED' FROM " + FIELDS + " WHERE channel = 'DRAFT'");
                LOG.info("Copied {} rule field(s) onto the released rules.", copied);
            }
            return migrated;
        }));
    }

    /** The foreign keys of the fields table that do not include {@code channel}: the ones from before channels. */
    List<String> oldForeignKeys() {
        Map<String, Set<String>> columnsByConstraint = jdbc.query(
                        "SELECT tc.constraint_name, k.column_name FROM information_schema.table_constraints tc "
                                + "JOIN information_schema.key_column_usage k ON k.constraint_name = tc.constraint_name "
                                + "AND k.table_name = tc.table_name "
                                + "WHERE LOWER(tc.table_name) = ? AND tc.constraint_type = 'FOREIGN KEY'",
                        (row, index) -> Map.entry(row.getString(1), row.getString(2).toLowerCase(Locale.ROOT)), FIELDS)
                .stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toSet())));
        return columnsByConstraint.entrySet().stream()
                .filter(entry -> !entry.getValue().contains("channel"))
                .map(Map.Entry::getKey)
                .toList();
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
