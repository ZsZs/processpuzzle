package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.AppRoute;
import com.processpuzzle.app.domain.Layout;
import com.processpuzzle.app.domain.ModuleMount;
import com.processpuzzle.app.domain.RouteTarget;
import com.processpuzzle.app.domain.NavNode;
import com.processpuzzle.app.domain.Region;
import com.processpuzzle.app.domain.Theme;
import com.processpuzzle.app.domain.Widget;
import com.processpuzzle.app.domain.WidgetPlacement;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.model.AppDefinitionStatus;
import com.processpuzzle.app.model.AppLayout;
import com.processpuzzle.app.model.ColorScheme;
import com.processpuzzle.app.model.KeyAvailability;
import com.processpuzzle.app.model.LayoutDefinition;
import com.processpuzzle.app.model.LayoutPreset;
import com.processpuzzle.app.model.MaterialTheme;
import com.processpuzzle.app.model.NavItem;
import com.processpuzzle.app.model.RouteDefinition;
import com.processpuzzle.app.model.PageOfAppDefinition;
import com.processpuzzle.app.model.ProvisioningResult;
import com.processpuzzle.app.model.RegionDefinition;
import com.processpuzzle.app.model.RegionType;
import com.processpuzzle.app.model.Severity;
import com.processpuzzle.app.model.SidenavMode;
import com.processpuzzle.app.model.ThemeDefinition;
import com.processpuzzle.app.model.ValidationProblem;
import com.processpuzzle.app.model.ValidationResult;
import com.processpuzzle.shared.model.WidgetInstance;
import com.processpuzzle.app.usecase.AppValidationProblem;
import com.processpuzzle.app.usecase.ImportOutcome;
import com.processpuzzle.app.model.OrganizationUpdate;
import com.processpuzzle.platformadmin.usecase.KeyCheckOutcome;
import com.processpuzzle.platformadmin.usecase.OrganizationDetails;
import com.processpuzzle.shared.model.ImportResult;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/**
 * Translates between the generated API models and the domain.
 *
 * <p>The enum-valued fields of the API models become plain strings in the domain — see
 * {@link Theme} for why — so the conversion is asymmetric: reading uses {@code fromValue}, which
 * would throw on a value that is no longer part of the enum. {@code toModel} therefore tolerates
 * unknown strings by dropping them rather than failing the whole read, so one stale field in a
 * persisted blob cannot make an app definition unreadable.
 */
@Component
public class AppMapper {

    // --- input -> domain ------------------------------------------------------------------

    public AppGraph toDomainGraph(AppDefinitionInput input) {
        if (input == null) {
            return AppGraph.empty();
        }
        return new AppGraph(
                toDomainTheme(input.getTheme()),
                toDomainLayout(input.getLayout()),
                toDomainRegions(input.getRegions()),
                toDomainRoutes(input.getRoutes()),
                toDomainMounts(input.getModules()));
    }

    private Theme toDomainTheme(ThemeDefinition theme) {
        if (theme == null) {
            return null;
        }
        return new Theme(
                theme.getMaterialTheme() == null ? null : theme.getMaterialTheme().getValue(),
                theme.getColorScheme() == null ? null : theme.getColorScheme().getValue(),
                theme.getTokenOverrides(),
                theme.getLogoUrl(),
                theme.getFaviconUrl());
    }

    private Layout toDomainLayout(LayoutDefinition layout) {
        if (layout == null) {
            return null;
        }
        return new Layout(
                layout.getPreset() == null ? null : layout.getPreset().getValue(),
                layout.getSidenavMode() == null ? null : layout.getSidenavMode().getValue(),
                layout.getSidenavCollapsible(),
                layout.getSidenavOpenByDefault(),
                layout.getContentMaxWidth());
    }

    private List<Region> toDomainRegions(List<RegionDefinition> regions) {
        if (regions == null) {
            return List.of();
        }
        return regions.stream().filter(region -> region != null && region.getType() != null)
                .map(region -> new Region(
                        region.getType().getValue(),
                        toDomainNavItems(region.getNavItems()),
                        toDomainWidgets(region.getWidgets())))
                .toList();
    }

    private List<NavNode> toDomainNavItems(List<NavItem> navItems) {
        if (navItems == null) {
            return List.of();
        }
        return navItems.stream().filter(Objects::nonNull)
                .map(item -> new NavNode(
                        item.getId(),
                        item.getLabel(),
                        item.getTranslocoId(),
                        item.getIcon(),
                        item.getRoutePath(),
                        item.getRoles(),
                        toDomainNavItems(item.getChildren())))
                .toList();
    }

    private List<Widget> toDomainWidgets(List<WidgetInstance> widgets) {
        if (widgets == null) {
            return List.of();
        }
        return widgets.stream().filter(Objects::nonNull)
                .map(widget -> new Widget(
                        widget.getId(),
                        widget.getType(),
                        widget.getProps(),
                        toDomainPlacement(widget.getPlacement())))
                .toList();
    }

    private WidgetPlacement toDomainPlacement(WidgetInstance.PlacementEnum placement) {
        return placement == null ? WidgetPlacement.STANDALONE : WidgetPlacement.valueOf(placement.name());
    }

    public List<AppRoute> toDomainRoutes(List<RouteDefinition> routes) {
        if (routes == null) {
            return List.of();
        }
        return routes.stream().filter(Objects::nonNull)
                .map(route -> new AppRoute(
                        route.getPath(),
                        route.getTitle(),
                        route.getTranslocoId(),
                        route.getIcon(),
                        route.getRoles(),
                        toDomainTarget(route.getTarget())))
                .toList();
    }

    /**
     * A route with no target at all becomes an empty WIDGETS target rather than null, so the graph
     * never holds a route the renderer cannot ask a question of. Which fields a kind requires is
     * AppDefinitionValidator's business, not this mapper's.
     */
    private RouteTarget toDomainTarget(com.processpuzzle.app.model.RouteTarget target) {
        if (target == null) {
            return RouteTarget.ofWidgets(List.of());
        }
        return new RouteTarget(
                target.getKind() == null ? RouteTarget.Kind.WIDGETS : RouteTarget.Kind.valueOf(target.getKind().name()),
                toDomainWidgets(target.getWidgets()),
                target.getDocumentSlug(),
                target.getEntityName(),
                target.getEntityMode() == null ? null : RouteTarget.EntityMode.valueOf(target.getEntityMode().name()),
                target.getRsqlFilter());
    }

    private List<ModuleMount> toDomainMounts(List<com.processpuzzle.app.model.ModuleMount> mounts) {
        if (mounts == null) {
            return List.of();
        }
        return mounts.stream().filter(Objects::nonNull)
                .map(mount -> new ModuleMount(mount.getModuleKey(), mount.getBasePath()))
                .toList();
    }

    // --- domain -> model -----------------------------------------------------------------

    public com.processpuzzle.app.model.AppDefinition toModel(
            com.processpuzzle.app.domain.AppDefinition definition) {
        AppGraph graph = definition.getDraftGraph();
        com.processpuzzle.app.model.AppDefinition model =
                new com.processpuzzle.app.model.AppDefinition(definition.getId(), definition.getName());
        model.setOrgKey(definition.getOrgKey());
        model.setTranslocoId(definition.getTranslocoId());
        model.setDescription(definition.getDescription());
        model.setStatus(toModelStatus(definition));
        model.setVersion(definition.getRevision());
        model.setPublishedVersion(definition.getPublishedRevision());
        model.setCreatedAt(toOffsetDateTime(definition.getCreatedAt()));
        model.setUpdatedAt(toOffsetDateTime(definition.getUpdatedAt()));
        if (graph != null) {
            model.setTheme(toModelTheme(graph.theme()));
            model.setLayout(toModelLayout(graph.layout()));
            model.setRegions(toModelRegions(graph.regions()));
            model.setRoutes(toModelRoutes(graph.routes()));
            model.setModules(toModelMounts(graph.modules()));
        }
        return model;
    }

    /**
     * Maps a page of definitions with {@link #toModel(com.processpuzzle.app.domain.AppDefinition)},
     * so a list entry is the same complete graph the single-GET returns. The designer edits an
     * entity straight out of the list rather than re-fetching it by id, so a lighter projection
     * here would hand it a truncated object and the next full-replacement PUT would persist the
     * truncation.
     */
    public PageOfAppDefinition toModel(Page<com.processpuzzle.app.domain.AppDefinition> page) {
        List<com.processpuzzle.app.model.AppDefinition> content =
                page.getContent().stream().map(this::toModel).toList();
        return new PageOfAppDefinition()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .number(page.getNumber())
                .size(page.getSize());
    }

    /**
     * Builds the run-time shell payload. {@code graph} is passed in separately because it is the
     * role-filtered projection of either the draft or the published snapshot — not necessarily
     * what the entity currently holds.
     */
    public AppLayout toLayout(com.processpuzzle.app.domain.AppDefinition definition, AppGraph graph,
                              String defaultLocale) {
        AppLayout layout = new AppLayout(
                definition.getId(),
                definition.getOrgKey(),
                definition.getName(),
                toModelRegions(graph == null ? List.of() : graph.regions()));
        layout.setTranslocoId(definition.getTranslocoId());
        layout.setDefaultLocale(defaultLocale);
        layout.setVersion(definition.getRevision());
        if (graph != null) {
            layout.setTheme(toModelTheme(graph.theme()));
            layout.setLayout(toModelLayout(graph.layout()));
        }
        return layout;
    }

    public AppDefinitionStatus toModelStatus(com.processpuzzle.app.domain.AppDefinition definition) {
        return definition.isPublished() ? AppDefinitionStatus.PUBLISHED : AppDefinitionStatus.DRAFT;
    }

    private ThemeDefinition toModelTheme(Theme theme) {
        if (theme == null) {
            return null;
        }
        ThemeDefinition model = new ThemeDefinition();
        model.setMaterialTheme(materialTheme(theme.materialTheme()));
        model.setColorScheme(colorScheme(theme.colorScheme()));
        model.setTokenOverrides(theme.tokenOverrides());
        model.setLogoUrl(theme.logoUrl());
        model.setFaviconUrl(theme.faviconUrl());
        return model;
    }

    private LayoutDefinition toModelLayout(Layout layout) {
        if (layout == null) {
            return null;
        }
        LayoutDefinition model = new LayoutDefinition();
        model.setPreset(layoutPreset(layout.preset()));
        model.setSidenavMode(sidenavMode(layout.sidenavMode()));
        model.setSidenavCollapsible(layout.sidenavCollapsible());
        model.setSidenavOpenByDefault(layout.sidenavOpenByDefault());
        model.setContentMaxWidth(layout.contentMaxWidth());
        return model;
    }

    public List<RegionDefinition> toModelRegions(List<Region> regions) {
        if (regions == null) {
            return List.of();
        }
        return regions.stream()
                .map(this::toModelRegion)
                .filter(Objects::nonNull)
                .toList();
    }

    private RegionDefinition toModelRegion(Region region) {
        RegionType type = regionType(region.type());
        if (type == null) {
            return null;
        }
        RegionDefinition model = new RegionDefinition(type);
        model.setNavItems(toModelNavItems(region.navItems()));
        model.setWidgets(toModelWidgets(region.widgets()));
        return model;
    }

    private List<NavItem> toModelNavItems(List<NavNode> navItems) {
        if (navItems == null) {
            return List.of();
        }
        return navItems.stream().map(node -> {
            NavItem model = new NavItem(node.id(), node.label());
            model.setTranslocoId(node.translocoId());
            model.setIcon(node.icon());
            model.setRoutePath(node.routePath());
            model.setRoles(node.roles());
            model.setChildren(toModelNavItems(node.children()));
            return model;
        }).toList();
    }

    private List<WidgetInstance> toModelWidgets(List<Widget> widgets) {
        if (widgets == null) {
            return List.of();
        }
        return widgets.stream().map(widget -> {
            WidgetInstance model = new WidgetInstance(widget.id(), widget.type());
            model.setProps(widget.props());
            model.setPlacement(WidgetInstance.PlacementEnum.fromValue(widget.placement().name()));
            return model;
        }).toList();
    }

    private List<RouteDefinition> toModelRoutes(List<AppRoute> routes) {
        if (routes == null) {
            return List.of();
        }
        return routes.stream().map(this::toModel).toList();
    }

    public RouteDefinition toModel(AppRoute route) {
        RouteDefinition model = new RouteDefinition(route.path(), route.title(), toModelTarget(route.target()));
        model.setTranslocoId(route.translocoId());
        model.setIcon(route.icon());
        model.setRoles(route.roles());
        return model;
    }

    private com.processpuzzle.app.model.RouteTarget toModelTarget(RouteTarget target) {
        if (target == null) {
            return null;
        }
        com.processpuzzle.app.model.RouteTarget model = new com.processpuzzle.app.model.RouteTarget(
                com.processpuzzle.app.model.RouteTarget.KindEnum.fromValue(target.kind().name()));
        model.setWidgets(toModelWidgets(target.widgets()));
        model.setDocumentSlug(target.documentSlug());
        model.setEntityName(target.entityName());
        if (target.entityMode() != null) {
            model.setEntityMode(com.processpuzzle.app.model.RouteTarget.EntityModeEnum.fromValue(target.entityMode().name()));
        }
        model.setRsqlFilter(target.rsqlFilter());
        return model;
    }

    private List<com.processpuzzle.app.model.ModuleMount> toModelMounts(List<ModuleMount> mounts) {
        if (mounts == null) {
            return List.of();
        }
        return mounts.stream()
                .map(mount -> new com.processpuzzle.app.model.ModuleMount(mount.moduleKey(), mount.basePath()))
                .toList();
    }

    // --- modules -------------------------------------------------------------------------

    public com.processpuzzle.app.model.ModuleDefinition toModel(
            com.processpuzzle.app.domain.ModuleDefinition module) {
        com.processpuzzle.app.model.ModuleDefinition model =
                new com.processpuzzle.app.model.ModuleDefinition(
                        module.getKey(), module.getName(), module.getOrgKey(), module.getVersion());
        model.setTranslocoId(module.getTranslocoId());
        model.setDescription(module.getDescription());
        model.setTranslocoScope(module.getTranslocoScope());
        model.setRoutes(toModelRoutes(module.getRoutes()));
        model.setCreatedAt(toOffsetDateTime(module.getCreatedAt()));
        model.setUpdatedAt(toOffsetDateTime(module.getUpdatedAt()));
        return model;
    }

    /**
     * Copies the editable fields of a module input onto an entity. {@code key} and {@code orgKey} are
     * deliberately absent: the contract calls the key immutable, because it is what an
     * {@code AppDefinition.modules} entry references.
     */
    public void applyToModule(com.processpuzzle.app.domain.ModuleDefinition module,
                              com.processpuzzle.app.model.ModuleDefinitionInput input) {
        module.setName(input.getName());
        module.setTranslocoId(input.getTranslocoId());
        module.setDescription(input.getDescription());
        module.setTranslocoScope(input.getTranslocoScope());
        module.setRoutes(toDomainRoutes(input.getRoutes()));
    }

    // --- organizations -------------------------------------------------------------------

    public com.processpuzzle.app.model.Organization toModel(
            com.processpuzzle.platformadmin.domain.Organization organization) {
        com.processpuzzle.app.model.Organization model = new com.processpuzzle.app.model.Organization(
                organization.getKey(),
                organization.getName(),
                com.processpuzzle.app.model.OrganizationStatus.fromValue(organization.getStatus().name()));
        model.setDescription(organization.getDescription());
        model.setContactEmail(organization.getContactEmail());
        model.setDefaultLocale(organization.getDefaultLocale());
        model.setCreatedAt(toOffsetDateTime(organization.getCreatedAt()));
        model.setUpdatedAt(toOffsetDateTime(organization.getUpdatedAt()));
        return model;
    }

    public ProvisioningResult toModel(com.processpuzzle.platformadmin.domain.Organization organization,
                                      com.processpuzzle.app.domain.AppDefinition starterApp) {
        return new ProvisioningResult(toModel(organization), toModel(starterApp));
    }

    /**
     * Maps the tenant-facing update payload onto the use-case-level record the relocated
     * {@code UpdateOrganization} takes. The use case deliberately accepts neither contract's DTO —
     * {@code app.model.OrganizationUpdate} belongs to this Modulith module, and depending on it from
     * {@code platformadmin} would close a cycle. See {@code OrganizationDetails}.
     */
    public OrganizationDetails toDetails(OrganizationUpdate input) {
        return new OrganizationDetails(input.getName(), input.getDescription(),
                input.getContactEmail(), input.getDefaultLocale());
    }

    public KeyAvailability toModel(KeyCheckOutcome outcome) {
        KeyAvailability model = new KeyAvailability(outcome.key(), outcome.available());
        model.setErrorId(outcome.errorId());
        model.setSuggestions(outcome.suggestions());
        return model;
    }

    // --- outcomes ------------------------------------------------------------------------

    /**
     * {@code valid} reports whether the definition may be persisted, not whether the list is empty: a
     * definition carrying only warnings and advice from the organization's rules is valid.
     */
    public ValidationResult toModel(List<AppValidationProblem> problems) {
        List<ValidationProblem> models = problems.stream().map(problem -> {
            ValidationProblem model = new ValidationProblem(problem.errorId(), problem.errorText());
            model.setPath(problem.path());
            model.setSeverity(Severity.fromValue(problem.severity().name()));
            return model;
        }).toList();
        return new ValidationResult(AppValidationProblem.blocking(problems).isEmpty(), models);
    }

    public ImportResult toModel(ImportOutcome outcome) {
        return new ImportResult()
                .created(outcome.created())
                .updated(outcome.updated())
                .errors(outcome.errors());
    }

    // --- primitives ----------------------------------------------------------------------

    private MaterialTheme materialTheme(String raw) {
        try {
            return raw == null ? null : MaterialTheme.fromValue(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ColorScheme colorScheme(String raw) {
        try {
            return raw == null ? null : ColorScheme.fromValue(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private LayoutPreset layoutPreset(String raw) {
        try {
            return raw == null ? null : LayoutPreset.fromValue(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private SidenavMode sidenavMode(String raw) {
        try {
            return raw == null ? null : SidenavMode.fromValue(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private RegionType regionType(String raw) {
        try {
            return raw == null ? null : RegionType.fromValue(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
