package com.processpuzzle.app.usecase;

import com.processpuzzle.app.adapter.inbound.AppMapper;
import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.usecase.exception.AppDefinitionAlreadyExistsException;
import com.processpuzzle.app.usecase.exception.AppDefinitionInvalidException;
import com.processpuzzle.app.usecase.exception.UnknownTenantException;
import com.processpuzzle.app.usecase.service.AppDefinitionValidator;
import com.processpuzzle.app.usecase.port.TenantDirectory;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ObjectProvider<TenantDirectory> tenantDirectoryProvider;
    private final AppDefinitionValidator validator;
    private final OrganizationGuard guard;
    private final AppMapper mapper;

    public CreateAppDefinition(AppDefinitionRepository repository,
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

    public AppDefinition execute(String orgKey, AppDefinitionInput input) {
        guard.requireDesign(orgKey);
        if (!tenantDirectory().exists(orgKey)) {
            throw new UnknownTenantException(orgKey);
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

    /**
     * Resolved per call rather than in the constructor: the directory is contributed by the
     * application, and a library must stay usable when it is absent. The default port permits, so an
     * unwired deployment behaves as it did before the check existed.
     */
    private TenantDirectory tenantDirectory() {
        TenantDirectory directory = tenantDirectoryProvider.getIfAvailable();
        return directory == null ? new TenantDirectory() { } : directory;
    }
}
