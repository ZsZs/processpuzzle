package com.processpuzzle.starter.adapter.inbound;

import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import com.processpuzzle.starter.domain.DefinitionProvenanceRepository;
import com.processpuzzle.starter.domain.InstalledStarterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes this feature's tenant-scoped rows — installed starters and definition provenance — when a
 * tenant is deleted. {@link TransactionPhase#BEFORE_COMMIT}, as in base-app's handler of the same
 * name: the deletes join the deleting transaction and commit with it; see
 * {@link OrganizationDeletedEvent}.
 */
@Component("starterTenantDataCleaner")
public class TenantDataCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(TenantDataCleaner.class);

    private final InstalledStarterRepository installedStarterRepository;
    private final DefinitionProvenanceRepository provenanceRepository;

    public TenantDataCleaner(InstalledStarterRepository installedStarterRepository,
                             DefinitionProvenanceRepository provenanceRepository) {
        this.installedStarterRepository = installedStarterRepository;
        this.provenanceRepository = provenanceRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrganizationDeleted(OrganizationDeletedEvent event) {
        LOG.info("Organization '{}' deleted; removing its installed starters and their provenance.", event.orgKey());
        provenanceRepository.deleteByOrgKey(event.orgKey());
        installedStarterRepository.deleteByOrgKey(event.orgKey());
    }
}
