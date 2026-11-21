package com.processpuzzle.baseentity.unit;

import com.processpuzzle.baseentity.unit.domain.OrganizationUnit;
import com.processpuzzle.baseentity.unit.domain.OrganizationUnitRepository;
import com.processpuzzle.core.quantity.FundamentalTypeException;
import com.processpuzzle.core.quantity.UnitCatalog;
import com.processpuzzle.core.quantity.UnitCatalog.ExtraUnit;
import com.processpuzzle.core.quantity.UnitCatalogProvider;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The catalog of each organization: the standard catalog and the units the organization added, retired ones included
 * (a retired unit is no longer offered, but values stored with it must stay readable).
 *
 * <p>Built once per organization and kept, since every write of a measure reads it; a change to the organization's units
 * drops it — after the change has committed, so a read in between cannot rebuild the old one and keep it. The cache lives in
 * this process, which is how the platform runs (one backend per stack); a second node would need to learn of the change.
 */
@Component
public class OrganizationUnitCatalogs implements UnitCatalogProvider {

    private static final Logger LOG = LoggerFactory.getLogger(OrganizationUnitCatalogs.class);

    private final OrganizationUnitRepository units;
    private final Map<String, UnitCatalog> catalogs = new ConcurrentHashMap<>();

    public OrganizationUnitCatalogs(OrganizationUnitRepository units) {
        this.units = units;
    }

    @Override
    public UnitCatalog forOrganization(String orgKey) {
        if (orgKey == null) {
            return UnitCatalog.standard();
        }
        return catalogs.computeIfAbsent(orgKey, this::build);
    }

    /** Forgets the organization's catalog once the transaction in progress commits (at once when there is none). */
    public void evict(String orgKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            catalogs.remove(orgKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                catalogs.remove(orgKey);
            }
        });
    }

    private UnitCatalog build(String orgKey) {
        UnitCatalog catalog = UnitCatalog.standard();
        for (OrganizationUnit unit : units.findAllByOrgKeyOrderByCode(orgKey)) {
            try {
                catalog = catalog.withUnits(List.of(new ExtraUnit(unit.getKind(), unit.getCode(), unit.getSymbol(), unit.getName(), unit.getFactor(), unit.getDivisor())));
            } catch (FundamentalTypeException e) {
                // Creating a unit refuses what the catalog would, so this is a row written some other way. Leaving it out keeps the
                // organization's other units and its standard ones working; the values that name it then fail validation, loudly.
                LOG.warn("Unit '{}' of organization '{}' is not part of its catalog: {}", unit.getCode(), orgKey, e.getMessage());
            }
        }
        return catalog;
    }
}
