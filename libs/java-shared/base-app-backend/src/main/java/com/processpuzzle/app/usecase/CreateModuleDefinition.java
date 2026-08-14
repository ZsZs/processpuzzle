package com.processpuzzle.app.usecase;

import com.processpuzzle.app.adapter.inbound.AppMapper;
import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.domain.OrganizationRepository;
import com.processpuzzle.app.model.ModuleDefinitionInput;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionAlreadyExistsException;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionInvalidException;
import com.processpuzzle.app.usecase.exception.OrganizationNotFoundException;
import com.processpuzzle.app.usecase.service.AppDefinitionValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Creates a module in an organization. A module is an aggregate of its own — nothing about creating
 * one touches an {@link com.processpuzzle.app.domain.AppDefinition}, and no app has to mount it for
 * it to exist.
 */
@Service
@Transactional
public class CreateModuleDefinition {

    private final ModuleDefinitionRepository repository;
    private final OrganizationRepository organizationRepository;
    private final AppDefinitionValidator validator;
    private final OrganizationGuard guard;
    private final AppMapper mapper;

    public CreateModuleDefinition(ModuleDefinitionRepository repository,
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

    public ModuleDefinition execute(String orgKey, ModuleDefinitionInput input) {
        guard.requireDesign(orgKey);
        if (!organizationRepository.existsById(orgKey)) {
            throw new OrganizationNotFoundException(orgKey);
        }
        // Same reason as CreateAppDefinition: save merges for an assigned id, so without this a
        // duplicate create would silently overwrite the existing module.
        if (repository.existsByOrgKeyAndKey(orgKey, input.getKey())) {
            throw new ModuleDefinitionAlreadyExistsException(orgKey, input.getKey());
        }

        List<AppValidationProblem> blockers =
                AppValidationProblem.blocking(validator.validateModule(orgKey, input));
        if (!blockers.isEmpty()) {
            throw new ModuleDefinitionInvalidException(orgKey, input.getKey(), blockers);
        }

        ModuleDefinition module = new ModuleDefinition(orgKey, input.getKey(), input.getName());
        mapper.applyToModule(module, input);
        return repository.save(module);
    }
}
