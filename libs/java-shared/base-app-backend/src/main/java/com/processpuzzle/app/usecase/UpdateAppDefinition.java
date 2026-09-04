package com.processpuzzle.app.usecase;

import com.processpuzzle.app.adapter.inbound.AppMapper;
import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.usecase.exception.AppDefinitionInvalidException;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.app.usecase.service.AppDefinitionValidator;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Replaces the draft revision of an app definition.
 *
 * <p>Two properties this deliberately preserves:
 *
 * <ul>
 *   <li>The published snapshot is untouched, so end users keep seeing the last published revision
 *       while the designer works. This is the whole point of holding two snapshots.
 *   <li>The revision counter is bumped explicitly here — not by Hibernate — which is what keeps
 *       {@code status} meaningful: a definition is {@code PUBLISHED} exactly while
 *       {@code publishedRevision == revision}.
 * </ul>
 *
 * <p>There is no optimistic-locking check: {@code AppDefinitionInput} carries no version and the
 * contract defines no {@code If-Match} header, so a client has no way to tell us which revision it
 * edited. Concurrent designers are last-write-wins.
 */
@Service
@Transactional
public class UpdateAppDefinition {

    private final AppDefinitionRepository repository;
    private final AppDefinitionValidator validator;
    private final OrganizationGuard guard;
    private final AppMapper mapper;

    public UpdateAppDefinition(AppDefinitionRepository repository,
                               AppDefinitionValidator validator,
                               OrganizationGuard guard,
                               AppMapper mapper) {
        this.repository = repository;
        this.validator = validator;
        this.guard = guard;
        this.mapper = mapper;
    }

    public AppDefinition execute(String orgKey, String appId, AppDefinitionInput input) {
        guard.requireDesign(orgKey);
        AppDefinition existing = repository.findByOrgKeyAndId(orgKey, appId)
                .orElseThrow(() -> new AppDefinitionNotFoundException(orgKey, appId));

        List<AppValidationProblem> blockers =
                AppValidationProblem.blocking(validator.validate(orgKey, input));
        if (!blockers.isEmpty()) {
            throw new AppDefinitionInvalidException(orgKey, appId, blockers);
        }

        existing.replaceDraft(input.getName(), input.getTranslocoId(), input.getDescription(),
                mapper.toDomainGraph(input));
        return repository.save(existing);
    }
}
