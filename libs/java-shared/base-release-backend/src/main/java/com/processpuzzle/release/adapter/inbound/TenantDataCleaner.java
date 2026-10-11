package com.processpuzzle.release.adapter.inbound;

import com.processpuzzle.release.domain.OrgReleaseItemRepository;
import com.processpuzzle.release.domain.OrgReleaseRepository;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes this feature's tenant-scoped rows — the release history — when a tenant is deleted.
 * {@link TransactionPhase#BEFORE_COMMIT}, as in base-app's handler of the same name: the deletes join
 * the deleting transaction and commit with it; see {@link OrganizationDeletedEvent}. The released
 * definitions themselves are each feature's own rows, removed by its own cleaner.
 */
@Component("releaseTenantDataCleaner")
public class TenantDataCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(TenantDataCleaner.class);

    private final OrgReleaseRepository releaseRepository;
    private final OrgReleaseItemRepository itemRepository;

    public TenantDataCleaner(OrgReleaseRepository releaseRepository, OrgReleaseItemRepository itemRepository) {
        this.releaseRepository = releaseRepository;
        this.itemRepository = itemRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrganizationDeleted(OrganizationDeletedEvent event) {
        LOG.info("Organization '{}' deleted; removing its release history.", event.orgKey());
        itemRepository.eraseByOrgKey(event.orgKey());
        releaseRepository.eraseByOrgKey(event.orgKey());
    }
}
