package com.processpuzzle.app.usecase;

import com.processpuzzle.app.adapter.inbound.AppMapper;
import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.usecase.exception.AppDefinitionAlreadyExistsException;
import com.processpuzzle.app.usecase.exception.AppDefinitionInvalidException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationNotFoundException;
import com.processpuzzle.app.usecase.service.AppDefinitionValidator;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.platformadmin.usecase.OrganizationGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Creates an app definition in an organization. Provisioning already creates one starter definition;
 * this serves organizations hosting more than one application.
 */
@Service
@Transactional
public class CreateAppDefinition {

    private final AppDefinitionRepository repository;
    private final OrganizationRepository organizationRepository;
    private final AppDefinitionValidator validator;
    private final OrganizationGuard guard;
    private final AppMapper mapper;

    public CreateAppDefinition(AppDefinitionRepository repository,
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

    public AppDefinition execute(String orgKey, AppDefinitionInput input) {
        guard.requireDesign(orgKey);
        if (!organizationRepository.existsById(orgKey)) {
            throw new OrganizationNotFoundException(orgKey);
        }
        // Must precede save: JpaRepository.save merges rather than persists for an assigned id, so a
        // duplicate create would silently overwrite instead of conflicting.
        if (repository.existsByOrgKeyAndId(orgKey, input.getId())) {
            throw new AppDefinitionAlreadyExistsException(orgKey, input.getId());
        }

        // Only ERROR-severity problems reject the write: the organization's own rules also report
        // warnings and advice, which a draft is allowed to carry.
        List<AppValidationProblem> blockers =
                AppValidationProblem.blocking(validator.validate(orgKey, input));
        if (!blockers.isEmpty()) {
            throw new AppDefinitionInvalidException(orgKey, input.getId(), blockers);
        }

        return repository.save(new AppDefinition(
                orgKey,
                input.getId(),
                input.getName(),
                input.getTranslocoId(),
                input.getDescription(),
                mapper.toDomainGraph(input)));
    }
}
