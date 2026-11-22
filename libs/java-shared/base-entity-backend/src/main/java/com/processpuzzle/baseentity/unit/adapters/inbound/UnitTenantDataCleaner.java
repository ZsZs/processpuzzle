package com.processpuzzle.baseentity.unit.adapters.inbound;

import com.processpuzzle.baseentity.unit.OrganizationUnitCatalogs;
import com.processpuzzle.baseentity.unit.domain.OrganizationUnitRepository;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes the units of an erased tenant. {@code BEFORE_COMMIT}, so the delete joins the erasing transaction (an
 * {@code AFTER_COMMIT} listener's writes would be discarded without an error). Named, because every feature declares a
 * cleaner and an application that assembles several would otherwise have two beans of one name.
 */
@Component("unitTenantDataCleaner")
class UnitTenantDataCleaner {

    private final OrganizationUnitRepository units;
    private final OrganizationUnitCatalogs catalogs;

    UnitTenantDataCleaner(OrganizationUnitRepository units, OrganizationUnitCatalogs catalogs) {
        this.units = units;
        this.catalogs = catalogs;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void onOrganizationDeleted(OrganizationDeletedEvent event) {
        units.eraseByOrgKey(event.orgKey());
        catalogs.evict(event.orgKey());
    }
}
