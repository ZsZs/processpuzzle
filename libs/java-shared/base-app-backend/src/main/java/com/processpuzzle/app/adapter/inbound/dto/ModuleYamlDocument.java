package com.processpuzzle.app.adapter.inbound.dto;

import com.processpuzzle.app.model.ModuleDefinitionInput;
import java.util.List;

/**
 * Root of a starter bundle's module file: the {@code moduleDefinitions} entries of a
 * {@link DefaultAppsDocument}, on their own, so an author writes a module exactly as the bundled
 * default-apps files do.
 *
 * <p>The entries are the generated {@code ModuleDefinitionInput}, so the import feeds the same validator
 * and mapper as the REST path.
 *
 * @param moduleDefinitions the modules in the file
 */
public record ModuleYamlDocument(List<ModuleDefinitionInput> moduleDefinitions) {

    public ModuleYamlDocument {
        moduleDefinitions = moduleDefinitions == null ? List.of() : List.copyOf(moduleDefinitions);
    }
}
