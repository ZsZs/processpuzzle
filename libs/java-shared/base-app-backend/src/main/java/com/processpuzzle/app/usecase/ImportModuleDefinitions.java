package com.processpuzzle.app.usecase;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.app.adapter.inbound.AppMapper;
import com.processpuzzle.app.adapter.inbound.dto.ModuleYamlDocument;
import com.processpuzzle.app.domain.AppRoles;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.model.ModuleDefinitionInput;
import com.processpuzzle.app.usecase.exception.UnknownTenantException;
import com.processpuzzle.app.usecase.port.TenantDirectory;
import com.processpuzzle.app.usecase.service.AppDefinitionValidator;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports module definitions from a YAML file, all-or-nothing, as {@link ImportAppDefinitions} does for apps: if
 * any entry fails validation nothing is persisted and every problem is reported.
 *
 * <p>Imported modules replace the draft of a module with the same key and are created as drafts otherwise, so
 * what end users see changes with the organization's next release, not with the import.
 */
@Service
public class ImportModuleDefinitions {

    private final ModuleDefinitionRepository repository;
    private final ObjectProvider<TenantDirectory> tenantDirectoryProvider;
    private final AppDefinitionValidator validator;
    private final OrganizationGuard guard;
    private final AppMapper mapper;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public ImportModuleDefinitions(ModuleDefinitionRepository repository,
                                   ObjectProvider<TenantDirectory> tenantDirectoryProvider,
                                   AppDefinitionValidator validator,
                                   OrganizationGuard guard,
                                   AppMapper mapper) {
        this.repository = repository;
        this.tenantDirectoryProvider = tenantDirectoryProvider;
        this.validator = validator;
        this.guard = guard;
        this.mapper = mapper;
    }

    @Transactional
    public ImportOutcome execute(String orgKey, InputStream input) throws IOException {
        guard.requireDesign(orgKey);
        if (!tenantDirectory().exists(orgKey)) {
            throw new UnknownTenantException(orgKey);
        }

        ModuleYamlDocument document = yamlMapper.readValue(input, ModuleYamlDocument.class);

        List<String> errors = new ArrayList<>();
        Map<String, ModuleDefinitionInput> byKey = new LinkedHashMap<>();
        for (ModuleDefinitionInput entry : document.moduleDefinitions()) {
            if (entry == null) {
                errors.add("A module definition entry is null and was skipped.");
            } else if (entry.getKey() == null || entry.getKey().isBlank()) {
                errors.add("A module definition entry is missing 'key' and was skipped.");
            } else if (byKey.put(entry.getKey(), entry) != null) {
                errors.add("Duplicate module definition key within the import file: '" + entry.getKey() + "'.");
            }
        }

        // Blocking problems only, for the reason given on ImportAppDefinitions.
        for (ModuleDefinitionInput entry : byKey.values()) {
            AppValidationProblem.blocking(validator.validateModule(orgKey, entry)).forEach(problem ->
                    errors.add("'" + entry.getKey() + "' " + problem.path() + ": " + problem.errorText()));
        }

        if (!errors.isEmpty()) {
            return ImportOutcome.rejected(errors);
        }

        int created = 0;
        int updated = 0;
        for (ModuleDefinitionInput entry : byKey.values()) {
            Optional<ModuleDefinition> existing = repository.findByOrgKeyAndKeyAndChannel(orgKey, entry.getKey(), Channel.DRAFT);
            ModuleDefinition module;
            if (existing.isPresent()) {
                module = existing.get();
                mapper.applyToModule(module, entry);
                module.markEdited();
                updated++;
            } else {
                module = new ModuleDefinition(orgKey, entry.getKey(), entry.getName());
                mapper.applyToModule(module, entry);
                created++;
            }
            guard.declareRoles(orgKey, AppRoles.DESCRIPTION, AppRoles.of(module));
            repository.save(module);
        }

        return new ImportOutcome(created, updated, errors);
    }

    /** Resolved per call, for the reason given on {@link CreateModuleDefinition#execute}. */
    private TenantDirectory tenantDirectory() {
        TenantDirectory directory = tenantDirectoryProvider.getIfAvailable();
        return directory == null ? new TenantDirectory() { } : directory;
    }
}
