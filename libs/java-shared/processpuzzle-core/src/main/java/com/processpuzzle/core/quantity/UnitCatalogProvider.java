package com.processpuzzle.core.quantity;

/**
 * The unit catalog of one organization: the standard catalog plus the units the organization added. A port, so that a
 * feature that reads quantities (base-rule) needs no compile dependency on the one that stores the organization's
 * units (base-entity); an application without that feature has only the standard catalog.
 */
@FunctionalInterface
public interface UnitCatalogProvider {

    /** The catalog {@code orgKey}'s values are read in. Never null; the standard catalog when the organization has added nothing. */
    UnitCatalog forOrganization(String orgKey);

    /** The provider of an application that cannot add units. */
    static UnitCatalogProvider standardOnly() {
        return orgKey -> UnitCatalog.standard();
    }
}
