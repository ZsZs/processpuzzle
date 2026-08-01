package com.processpuzzle.app.adapter.inbound.dto;

import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.model.OrganizationInput;

import java.util.List;

/**
 * Root of a bundled {@code default-apps/<orgKey>-apps.yaml} file, read by
 * {@link com.processpuzzle.app.adapter.inbound.DefaultAppLoader} on startup.
 *
 * <p>A superset of {@link AppYamlDocument}: the {@code appDefinitions} entries are the very same
 * generated {@code AppDefinitionInput}, so a default-apps file can be fed to the YAML import endpoint
 * unchanged and passes through the same validator and mapper. The extra {@code organization} block is
 * what an import cannot carry — the import endpoint takes its tenant from the request path, whereas a
 * loader running at startup may have to create that tenant first.
 *
 * <p>{@code organization.key} is informational only. The owning organization is the part of the file
 * name before {@code -apps.yaml}, so a copied file cannot silently seed the tenant it was copied
 * from.
 *
 * @param organization the tenant to provision when it does not exist yet; may be {@code null}, in
 *     which case the loader provisions one named after the file's organization key
 * @param appDefinitions the definitions to create in that organization
 */
public record DefaultAppsDocument(OrganizationInput organization,
                                  List<AppDefinitionInput> appDefinitions) {

    public DefaultAppsDocument {
        appDefinitions = appDefinitions == null ? List.of() : List.copyOf(appDefinitions);
    }
}
