package com.processpuzzle.baseentity.definition.adapters.outbound;

import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionStatus;
import com.processpuzzle.core.definition.Channel;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Retires the {@code DRAFT} status from the definitions stored with it, keeping what it meant.
 *
 * <p>Before releases existed, {@code DRAFT} was what kept a definition off the running application. A release does
 * that now. So a definition stored as one is a draft that was never live: its draft row becomes {@code ACTIVE}, and
 * the released copy {@link EntityDefinitionChannelBackfill} made of it is removed. It then waits in the Release
 * section as a new definition, and stays off the running application until it is released — as before.
 *
 * <p>Idempotent: once no row has the status, it does nothing. Ordered after the channel backfill and ahead of
 * {@code DefaultEntityLoader}; like both, it never fails startup.
 */
@Component
public class EntityDefinitionStatusMigration {

    private static final Logger LOG = LoggerFactory.getLogger(EntityDefinitionStatusMigration.class);

    private final EntityDefinitionRepository repository;
    private final TransactionTemplate transaction;

    public EntityDefinitionStatusMigration(EntityDefinitionRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Order(6)
    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        try {
            migrateOnce();
        } catch (RuntimeException e) {
            LOG.warn("Failed to retire the DRAFT status of entity definitions; it is retried on the next start.", e);
        }
    }

    /** @return the number of definitions moved; 0 when none had the status */
    @SuppressWarnings("deprecation")
    public int migrateOnce() {
        // The callback always returns its count, so execute() never answers null here.
        int moved = Objects.requireNonNull(transaction.execute(status -> {
            List<BaseEntityDefinition> retired = repository.findAll().stream()
                .filter(definition -> definition.getStatus() == EntityDefinitionStatus.DRAFT)
                .toList();
            repository.deleteAll(retired.stream().filter(definition -> definition.getChannel() == Channel.RELEASED).toList());
            List<BaseEntityDefinition> drafts = retired.stream().filter(definition -> definition.getChannel() == Channel.DRAFT).toList();
            drafts.forEach(draft -> draft.setStatus(EntityDefinitionStatus.ACTIVE));
            repository.saveAll(drafts);
            return drafts.size();
        }));
        if (moved > 0) {
            LOG.info("Moved {} entity definition(s) off the retired DRAFT status: they are drafts awaiting their first release.", moved);
        }
        return moved;
    }
}
