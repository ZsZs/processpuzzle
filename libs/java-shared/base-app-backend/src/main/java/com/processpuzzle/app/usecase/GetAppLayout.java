package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.usecase.port.TenantDirectory;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.app.usecase.exception.AppNotPublishedException;
import com.processpuzzle.app.usecase.service.NavVisibilityFilter;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the run-time bootstrap payload for the application shell: theme, layout, the regions with
 * nav entries already filtered against the caller's roles, and the routes and module mounts the
 * caller may reach — everything the runtime host needs to render the published application from
 * one request.
 *
 * <p>Filtering happens here, server side, so a nav entry the user may not see never reaches the
 * browser. The corresponding route fetch is authorized independently by {@link GetRouteDefinition};
 * hiding the entry is not by itself access control.
 *
 * <p>{@code draft = true} serves the unpublished working copy for the designer's preview and
 * requires design permission, so preview and production run through the same interpreter rather
 * than two pipelines.
 */
@Service
@Transactional(readOnly = true)
public class GetAppLayout {

    private final AppDefinitionRepository repository;
    private final ObjectProvider<TenantDirectory> tenantDirectoryProvider;
    private final OrganizationGuard guard;
    private final NavVisibilityFilter navVisibility;

    public GetAppLayout(AppDefinitionRepository repository,
                        ObjectProvider<TenantDirectory> tenantDirectoryProvider,
                        OrganizationGuard guard,
                        NavVisibilityFilter navVisibility) {
        this.repository = repository;
        this.tenantDirectoryProvider = tenantDirectoryProvider;
        this.guard = guard;
        this.navVisibility = navVisibility;
    }

    public Result execute(String orgKey, String appId, boolean draft) {
        if (draft) {
            guard.requireDesign(orgKey);
        } else {
            guard.requireAccess(orgKey);
        }

        AppDefinition definition = repository.findByOrgKeyAndId(orgKey, appId)
                .orElseThrow(() -> new AppDefinitionNotFoundException(orgKey, appId));

        AppGraph graph = definition.graphFor(draft);
        if (graph == null) {
            // Only reachable for draft = false: nothing has been published yet.
            throw new AppNotPublishedException(orgKey, appId);
        }

        AppGraph filtered = graph
                .withRegions(navVisibility.filterRegions(graph.regions()))
                .withRoutes(navVisibility.filterRoutes(graph.routes()));
        TenantDirectory directory = tenantDirectoryProvider.getIfAvailable();
        String defaultLocale = directory == null
                ? null
                : directory.find(orgKey).map(TenantDirectory.Tenant::defaultLocale).orElse(null);

        Long version = draft ? definition.getRevision() : definition.getPublishedRevision();
        return new Result(definition, filtered, defaultLocale, version);
    }

    /**
     * @param definition the entity, for its header fields and revision
     * @param graph the role-filtered projection of the requested revision
     * @param defaultLocale copied from the organization so the shell can set the Transloco language
     *                      from this one request
     * @param version the revision {@code graph} was taken from — the published revision for a
     *                published read, so a host can tell when a new one has been published
     */
    public record Result(AppDefinition definition, AppGraph graph, String defaultLocale, Long version) {

        /** A result for the draft, whose version is the definition's current revision. */
        public Result(AppDefinition definition, AppGraph graph, String defaultLocale) {
            this(definition, graph, defaultLocale, definition.getRevision());
        }
    }
}
