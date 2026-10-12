package com.processpuzzle.baseentity.definition.adapters.outbound;

import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.core.definition.Channel;
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
 * Moves {@code base_entity_definition} onto channels once, so that every entity type an organization had before
 * releases existed is both its draft and its release — it runs exactly as before.
 *
 * <p>Not {@code ChannelBackfill}, which copies rows in SQL: a definition's primary key is a generated UUID and its
 * attributes hang off it, so a released copy needs ids of its own, for itself and for each attribute. The copy is
 * therefore {@link BaseEntityDefinition#releasedCopyOf}, the same one a release makes.
 *
 * <p>What has to go is the old unique key on {@code (org_key, code)}: Hibernate's {@code ddl-auto: update} adds the
 * new one on {@code (org_key, code, channel)} but never drops a constraint, and the old one would refuse every
 * released copy. Its presence is also what says the table has not been migrated yet — so this runs once, and a
 * draft a designer creates afterwards is never released by a restart. A fresh database has no such key and is
 * left alone. The drop and the copies run in one transaction, so a failure leaves the table as it was, to be
 * retried on the next start.
 *
 * <p>Ordered ahead of {@code DefaultEntityLoader}, whose released copies the old key would refuse. Like the loader
 * it never fails startup.
 */
@Component
public class EntityDefinitionChannelBackfill {

    static final String TABLE = "base_entity_definition";
    private static final Set<String> OLD_KEY = Set.of("org_key", "code");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_]\\w{0,62}");
    private static final Logger LOG = LoggerFactory.getLogger(EntityDefinitionChannelBackfill.class);

    private final JdbcTemplate jdbc;
    private final EntityDefinitionRepository repository;
    private final TransactionTemplate transaction;

    public EntityDefinitionChannelBackfill(JdbcTemplate jdbc, EntityDefinitionRepository repository,
                                           PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Order(5)
    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        try {
            migrateOnce();
        } catch (RuntimeException e) {
            LOG.warn("Failed to move {} onto channels; the running application reads no entity definitions until it is.",
                TABLE, e);
        }
    }

    /** @return the number of released copies made; 0 when the table was migrated already, or never needed it */
    public int migrateOnce() {
        List<String> oldKeys = oldUniqueKeys();
        if (oldKeys.isEmpty()) {
            return 0;
        }
        Integer copied = transaction.execute(status -> {
            oldKeys.forEach(name -> jdbc.execute("ALTER TABLE " + TABLE + " DROP CONSTRAINT " + identifier(name))); // NOSONAR java:S2077 - identifier() checked
            Set<String> released = repository.findAll().stream()
                .filter(definition -> definition.getChannel() == Channel.RELEASED)
                .map(definition -> definition.getOrgKey() + '\u0000' + definition.getCode())
                .collect(Collectors.toSet());
            List<BaseEntityDefinition> copies = repository.findAll().stream()
                .filter(definition -> definition.getChannel() == Channel.DRAFT)
                .filter(draft -> !released.contains(draft.getOrgKey() + '\u0000' + draft.getCode()))
                .map(BaseEntityDefinition::releasedCopyOf)
                .toList();
            repository.saveAll(copies);
            return copies.size();
        });
        LOG.info("Moved {} onto channels: its {} definition(s) are now drafts, each with a released copy.", TABLE, copied);
        return copied == null ? 0 : copied;
    }

    /** The names of the unique constraints on exactly {@code (org_key, code)}; empty once they are gone. */
    public List<String> oldUniqueKeys() {
        Map<String, Set<String>> columnsByConstraint = jdbc.query(
                "SELECT tc.constraint_name, k.column_name FROM information_schema.table_constraints tc "
                    + "JOIN information_schema.key_column_usage k ON k.constraint_name = tc.constraint_name "
                    + "AND k.table_name = tc.table_name "
                    + "WHERE LOWER(tc.table_name) = ? AND tc.constraint_type = 'UNIQUE'",
                (row, index) -> Map.entry(row.getString(1), row.getString(2).toLowerCase(Locale.ROOT)), TABLE)
            .stream()
            .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toSet())));
        return columnsByConstraint.entrySet().stream()
            .filter(entry -> entry.getValue().equals(OLD_KEY))
            .map(Map.Entry::getKey)
            .toList();
    }

    private static String identifier(String name) {
        if (!IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Not a plain SQL identifier: '" + name + "'.");
        }
        return name;
    }
}
