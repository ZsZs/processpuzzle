package com.processpuzzle.app.adapter.inbound.dto;

import com.processpuzzle.app.model.AppDefinitionInput;

import java.util.List;

/**
 * Root of a YAML import/export file.
 *
 * <p>The entries are the generated {@code AppDefinitionInput} rather than a parallel hand-written
 * DTO, so the import path feeds the very same validator and mapper as the REST path. base-rule needs
 * its own {@code RuleYamlEntry} only because of null-tri-state handling on {@code override} and
 * {@code enabled}; nothing here has that problem.
 *
 * <p>Any {@code orgKey} in the file is ignored — imported definitions are scoped to the organization
 * in the request path, which is what makes an export from one tenant importable into another.
 *
 * @param appDefinitions the definitions in the file
 */
public record AppYamlDocument(List<AppDefinitionInput> appDefinitions) {

    public AppYamlDocument {
        appDefinitions = appDefinitions == null ? List.of() : List.copyOf(appDefinitions);
    }
}
