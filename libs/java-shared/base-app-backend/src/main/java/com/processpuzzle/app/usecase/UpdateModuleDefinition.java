package com.processpuzzle.app.usecase;

import com.processpuzzle.app.adapter.inbound.AppMapper;
import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.model.ModuleDefinitionInput;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionInvalidException;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionNotFoundException;
import com.processpuzzle.app.usecase.service.AppDefinitionValidator;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Full replacement of a module. The key in the path wins over any key in the body: the contract
 * calls it immutable because an {@code AppDefinition.modules} entry references it, and letting a PUT
 * rename it would silently unmount the module from every app that mounts it.
 *
 * <p>Unlike an app definition a module holds no draft/published pair, so there is nothing to keep
 * serving while the designer works. Modules are versioned by a counter only — the same last-write-wins
 * story as {@link UpdateAppDefinition}, for the same reason: the input carries no version.
 */
@Service
@Transactional
public class UpdateModuleDefinition {

    private final ModuleDefinitionRepository repository;
    private final AppDefinitionValidator validator;
    private final OrganizationGuard guard;
    private final AppMapper mapper;

    public UpdateModuleDefinition(ModuleDefinitionRepository repository,
                                  AppDefinitionValidator validator,
                                  OrganizationGuard guard,
                                  AppMapper mapper) {
        this.repository = repository;
        this.validator = validator;
        this.guard = guard;
        this.mapper = mapper;
    }

    public ModuleDefinition execute(String orgKey, String moduleKey, ModuleDefinitionInput input) {
        guard.requireDesign(orgKey);
        ModuleDefinition existing = repository.findByOrgKeyAndKey(orgKey, moduleKey)
                .orElseThrow(() -> new ModuleDefinitionNotFoundException(orgKey, moduleKey));

        List<AppValidationProblem> blockers =
                AppValidationProblem.blocking(validator.validateModule(orgKey, input));
        if (!blockers.isEmpty()) {
            throw new ModuleDefinitionInvalidException(orgKey, moduleKey, blockers);
        }

        mapper.applyToModule(existing, input);
        existing.markEdited();
        return repository.save(existing);
    }
}
