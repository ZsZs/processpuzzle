package com.processpuzzle.app.adapter.inbound.dto;

import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.model.ModuleDefinitionInput;

import java.util.List;

/**
 * Root of a bundled {@code default-apps/<orgKey>-apps.yaml} file, read by
 * {@link com.processpuzzle.app.adapter.inbound.DefaultAppLoader} on startup.
 *
 * <p>Identical in shape to {@link AppYamlDocument}: the {@code appDefinitions} entries are the very
 * same generated {@code AppDefinitionInput}, so a default-apps file can be fed to the YAML import
 * endpoint unchanged and passes through the same validator and mapper. The owning organization is the
 * part of the file name before {@code -apps.yaml}, so a copied file cannot silently seed the tenant
 * it was copied from.
 *
 * <p>It used to carry an {@code organization} block as well, which the loader provisioned when the
 * key was still free — base-app creating tenants. That block is now
 * {@code default-organizations/<orgKey>-organization.yaml} in platform-admin, which owns the
 * aggregate and whose loader runs first. A file naming an unknown tenant is skipped with a warning,
 * as every other feature's seed loader already did.
 *
 * <p>{@code moduleDefinitions} are seeded before the apps, so that a mount in the same file names a
 * module that already exists. Nothing depends on that order — a mount naming an unknown module is a
 * warning by design, see {@code AppDefinitionValidator#validateModuleMounts} — it only keeps the log of
 * a fresh startup free of warnings the file itself answers.
 *
 * @param moduleDefinitions the modules to create in that organization, before the apps that mount them
 * @param appDefinitions the definitions to create in that organization
 */
public record DefaultAppsDocument(List<ModuleDefinitionInput> moduleDefinitions,
                                  List<AppDefinitionInput> appDefinitions) {

    public DefaultAppsDocument {
        moduleDefinitions = moduleDefinitions == null ? List.of() : List.copyOf(moduleDefinitions);
        appDefinitions = appDefinitions == null ? List.of() : List.copyOf(appDefinitions);
    }
}
