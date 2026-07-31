package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.AppPage;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.app.usecase.exception.AppNotPublishedException;
import com.processpuzzle.app.usecase.exception.PageDefinitionNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves one page for a route — the lazy counterpart to {@link GetAppLayout}, mirroring how
 * base-rule loads only the rules for the context at hand.
 *
 * <p>A page that exists but that no nav entry the caller can see reaches is reported as missing,
 * not as forbidden: answering 403 would confirm that a page the caller may not reach exists.
 * Checking reachability rather than trusting the layout response also means a guessed page id does
 * not bypass the role filter.
 */
@Service
@Transactional(readOnly = true)
public class GetPageDefinition {

    private final AppDefinitionRepository repository;
    private final OrganizationGuard guard;

    public GetPageDefinition(AppDefinitionRepository repository, OrganizationGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    public AppPage execute(String orgKey, String appId, String pageId, boolean draft) {
        if (draft) {
            guard.requireDesign(orgKey);
        } else {
            guard.requireAccess(orgKey);
        }

        AppDefinition definition = repository.findByOrgKeyAndId(orgKey, appId)
                .orElseThrow(() -> new AppDefinitionNotFoundException(orgKey, appId));

        AppGraph graph = definition.graphFor(draft);
        if (graph == null) {
            throw new AppNotPublishedException(orgKey, appId);
        }

        AppPage page = graph.findPage(pageId);
        if (page == null || !guard.isPageReachable(graph.regions(), pageId)) {
            throw new PageDefinitionNotFoundException(orgKey, appId, pageId);
        }
        return page;
    }
}
