package com.processpuzzle.app.usecase;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.app.adapter.inbound.AppMapper;
import com.processpuzzle.app.adapter.inbound.dto.AppYamlDocument;
import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.platformadmin.usecase.OrganizationGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

/**
 * Exports one app definition as a YAML file.
 *
 * <p>Exports the <em>draft</em> revision. The contract does not say which, and the draft is the right
 * default: export is a design-time artifact and it round-trips with import, which also produces
 * drafts. Server-assigned fields (orgKey, status, version, timestamps) are omitted, so the file is
 * importable into any organization as-is.
 */
@Service
@Transactional(readOnly = true)
public class ExportAppDefinition {

    private final AppDefinitionRepository repository;
    private final OrganizationGuard guard;
    private final AppMapper mapper;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public ExportAppDefinition(AppDefinitionRepository repository, OrganizationGuard guard, AppMapper mapper) {
        this.repository = repository;
        this.guard = guard;
        this.mapper = mapper;
    }

    public byte[] execute(String orgKey, String appId) throws IOException {
        guard.requireDesign(orgKey);
        AppDefinition definition = repository.findByOrgKeyAndId(orgKey, appId)
                .orElseThrow(() -> new AppDefinitionNotFoundException(orgKey, appId));

        com.processpuzzle.app.model.AppDefinition model = mapper.toModel(definition);
        AppDefinitionInput entry = new AppDefinitionInput(model.getId(), model.getName());
        entry.setTranslocoId(model.getTranslocoId());
        entry.setDescription(model.getDescription());
        entry.setTheme(model.getTheme());
        entry.setLayout(model.getLayout());
        entry.setRegions(model.getRegions());
        entry.setRoutes(model.getRoutes());
        entry.setModules(model.getModules());

        return yamlMapper.writeValueAsBytes(new AppYamlDocument(List.of(entry)));
    }
}
