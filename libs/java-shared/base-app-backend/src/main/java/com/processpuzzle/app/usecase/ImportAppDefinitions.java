package com.processpuzzle.app.usecase;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.app.adapter.inbound.AppMapper;
import com.processpuzzle.app.adapter.inbound.dto.AppYamlDocument;
import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationNotFoundException;
import com.processpuzzle.app.usecase.service.AppDefinitionValidator;
import com.processpuzzle.platformadmin.usecase.OrganizationGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Imports app definitions from a YAML file, all-or-nothing: if any entry fails validation nothing is
 * persisted and every problem is reported.
 *
 * <p>Imported definitions land in the organization from the request path regardless of any
 * {@code orgKey} in the file, so an export from one tenant can be imported into another unchanged.
 * They arrive as drafts — the revision counter advances but the published snapshot is untouched — so
 * importing into a live tenant never changes what end users see until someone publishes.
 */
@Service
public class ImportAppDefinitions {

    private final AppDefinitionRepository repository;
    private final OrganizationRepository organizationRepository;
    private final AppDefinitionValidator validator;
    private final OrganizationGuard guard;
    private final AppMapper mapper;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public ImportAppDefinitions(AppDefinitionRepository repository,
                                OrganizationRepository organizationRepository,
                                AppDefinitionValidator validator,
                                OrganizationGuard guard,
                                AppMapper mapper) {
        this.repository = repository;
        this.organizationRepository = organizationRepository;
        this.validator = validator;
        this.guard = guard;
        this.mapper = mapper;
    }

    @Transactional
    public ImportOutcome execute(String orgKey, InputStream input) throws IOException {
        guard.requireDesign(orgKey);
        if (!organizationRepository.existsById(orgKey)) {
            throw new OrganizationNotFoundException(orgKey);
        }

        AppYamlDocument document = yamlMapper.readValue(input, AppYamlDocument.class);

        List<String> errors = new ArrayList<>();
        Map<String, AppDefinitionInput> byId = new LinkedHashMap<>();
        for (AppDefinitionInput entry : document.appDefinitions()) {
            String skipReason = skipReason(entry);
            if (skipReason != null) {
                errors.add(skipReason);
            } else if (byId.put(entry.getId(), entry) != null) {
                errors.add("Duplicate app definition id within the import file: '" + entry.getId() + "'.");
            }
        }

        // Blocking problems only: a warning from one of the organization's rules must not fail an
        // otherwise importable file, or a tenant could not import its own export.
        for (AppDefinitionInput entry : byId.values()) {
            AppValidationProblem.blocking(validator.validate(orgKey, entry)).forEach(problem ->
                    errors.add("'" + entry.getId() + "' " + problem.path() + ": " + problem.errorText()));
        }

        if (!errors.isEmpty()) {
            return ImportOutcome.rejected(errors);
        }

        int created = 0;
        int updated = 0;
        for (AppDefinitionInput entry : byId.values()) {
            Optional<AppDefinition> existing = repository.findByOrgKeyAndId(orgKey, entry.getId());
            AppDefinition definition;
            if (existing.isPresent()) {
                definition = existing.get();
                definition.replaceDraft(entry.getName(), entry.getTranslocoId(), entry.getDescription(),
                        mapper.toDomainGraph(entry));
                updated++;
            } else {
                definition = new AppDefinition(orgKey, entry.getId(), entry.getName(),
                        entry.getTranslocoId(), entry.getDescription(), mapper.toDomainGraph(entry));
                created++;
            }
            repository.save(definition);
        }

        return new ImportOutcome(created, updated, errors);
    }

    /** Why {@code entry} cannot be indexed by id, or {@code null} when it can. */
    private static String skipReason(AppDefinitionInput entry) {
        if (entry == null) {
            return "An app definition entry is null and was skipped.";
        }
        if (entry.getId() == null || entry.getId().isBlank()) {
            return "An app definition entry is missing 'id' and was skipped.";
        }
        return null;
    }
}
