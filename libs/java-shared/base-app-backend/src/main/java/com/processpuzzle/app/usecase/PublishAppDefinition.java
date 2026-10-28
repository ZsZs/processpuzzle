package com.processpuzzle.app.usecase;

import com.processpuzzle.app.adapter.inbound.AppMapper;
import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.usecase.exception.AppDefinitionInvalidException;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.app.usecase.service.AppAssetReleases;
import com.processpuzzle.app.usecase.service.AppTranslationReleases;
import com.processpuzzle.app.usecase.service.AppDefinitionValidator;
import com.processpuzzle.app.usecase.service.ModuleAssetSweeper;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Promotes the current draft to the published revision, making it what end users are served.
 *
 * <p>Re-validates before promoting, so an invalid definition can never go live even if it was
 * persisted by a path that skipped validation. Publishing does not bump the revision counter — see
 * {@link AppDefinition} for why that matters.
 *
 * <p>Publishing applies the same bar as saving: only {@code ERROR} rejects. A tenant that wants a
 * convention to stop a release rather than merely annotate a draft raises that rule's severity — the
 * decision belongs in the rule record, not in a second threshold here.
 *
 * <p>The app's assets are published with it: their records are copied from the draft channel to the released
 * one, and content a released record stops naming is handed to the sweeper. So are its translations.
 *
 * <p>Publishing an already-published, unedited definition is a no-op rather than an error: it is
 * idempotent, which is the friendlier behaviour for a designer's Publish button.
 */
@Service
@Transactional
public class PublishAppDefinition {

    private final AppDefinitionRepository repository;
    private final AppDefinitionValidator validator;
    private final AppAssetReleases assets;
    private final AppTranslationReleases translations;
    private final ModuleAssetSweeper sweeper;
    private final OrganizationGuard guard;
    private final AppMapper mapper;

    public PublishAppDefinition(AppDefinitionRepository repository,
                                AppDefinitionValidator validator,
                                AppAssetReleases assets,
                                AppTranslationReleases translations,
                                ModuleAssetSweeper sweeper,
                                OrganizationGuard guard,
                                AppMapper mapper) {
        this.repository = repository;
        this.validator = validator;
        this.assets = assets;
        this.translations = translations;
        this.sweeper = sweeper;
        this.guard = guard;
        this.mapper = mapper;
    }

    public AppDefinition execute(String orgKey, String appId) {
        guard.requireDesign(orgKey);
        AppDefinition existing = repository.findByOrgKeyAndId(orgKey, appId)
                .orElseThrow(() -> new AppDefinitionNotFoundException(orgKey, appId));

        List<AppValidationProblem> blockers = AppValidationProblem.blocking(
                validator.validateStored(orgKey, mapper.toModel(existing)));
        if (!blockers.isEmpty()) {
            throw new AppDefinitionInvalidException(orgKey, appId, blockers);
        }

        existing.publish();
        Set<String> dropped = new TreeSet<>();
        assets.release(orgKey, appId, dropped);
        translations.release(orgKey, appId);
        sweeper.sweep(orgKey, dropped);
        return repository.save(existing);
    }
}
