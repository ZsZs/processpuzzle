package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.AppRoute;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.app.usecase.exception.AppNotPublishedException;
import com.processpuzzle.app.usecase.exception.RouteDefinitionNotFoundException;
import com.processpuzzle.app.usecase.service.NavVisibilityFilter;
import com.processpuzzle.platformadmin.usecase.OrganizationGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves one route by its path — the lazy counterpart to {@link GetAppLayout}, mirroring how
 * base-rule loads only the rules for the context at hand.
 *
 * <p>A route that exists but that no nav entry the caller can see reaches is reported as missing,
 * not as forbidden: answering 403 would confirm that a route the caller may not reach exists.
 * Checking reachability rather than trusting the layout response also means a guessed route id does
 * not bypass the role filter.
 */
@Service
@Transactional(readOnly = true)
public class GetRouteDefinition {

    private final AppDefinitionRepository repository;
    private final OrganizationGuard guard;
    private final NavVisibilityFilter navVisibility;

    public GetRouteDefinition(AppDefinitionRepository repository,
                              OrganizationGuard guard,
                              NavVisibilityFilter navVisibility) {
        this.repository = repository;
        this.guard = guard;
        this.navVisibility = navVisibility;
    }

    public AppRoute execute(String orgKey, String appId, String routePath, boolean draft) {
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

        AppRoute route = graph.findRoute(routePath);
        if (route == null || !navVisibility.isRouteReachable(graph.regions(), routePath)) {
            throw new RouteDefinitionNotFoundException(orgKey, appId, routePath);
        }
        return route;
    }
}
