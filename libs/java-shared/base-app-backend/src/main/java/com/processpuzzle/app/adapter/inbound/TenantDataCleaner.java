package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.platformadmin.domain.event.OrganizationDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes this feature's tenant-scoped rows when a tenant is deleted.
 *
 * <p>{@code DeleteOrganization} used to call these two repositories itself. It cannot now — it lives
 * in {@code platform-admin}, which has no sight of base-app's internals and would be in a dependency
 * cycle if it had. So the cascade arrives as {@link OrganizationDeletedEvent} and each feature
 * removes its own rows, which is what the old implementation's Javadoc already recommended.
 *
 * <p>{@link TransactionPhase#BEFORE_COMMIT} is load-bearing and not interchangeable with the
 * {@code AFTER_COMMIT} used elsewhere in this platform. Before commit, this handler runs inside the
 * deleting transaction, so these deletes commit atomically with the organization row — a tenant
 * cannot end up removed with its app definitions still present, or vice versa. An
 * {@code AFTER_COMMIT} listener runs with that transaction already completed, and writes performed
 * on it are discarded without an error; the symptom is silent orphaned data, which is precisely what
 * this handler must not produce.
 *
 * <p>The corollary is that throwing here rolls the deletion back, which is the intended behavior: if
 * a tenant's apps cannot be removed, the tenant should not disappear either.
 */
@Component
public class TenantDataCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(TenantDataCleaner.class);

    private final AppDefinitionRepository appDefinitionRepository;
    private final ModuleDefinitionRepository moduleDefinitionRepository;

    public TenantDataCleaner(AppDefinitionRepository appDefinitionRepository,
                             ModuleDefinitionRepository moduleDefinitionRepository) {
        this.appDefinitionRepository = appDefinitionRepository;
        this.moduleDefinitionRepository = moduleDefinitionRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrganizationDeleted(OrganizationDeletedEvent event) {
        LOG.info("Organization '{}' deleted; removing its app and module definitions.", event.orgKey());
        appDefinitionRepository.deleteByOrgKey(event.orgKey());
        moduleDefinitionRepository.deleteByOrgKey(event.orgKey());
    }
}
