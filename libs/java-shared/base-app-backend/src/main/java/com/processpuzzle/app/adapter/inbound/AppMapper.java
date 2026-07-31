package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.AppPage;
import com.processpuzzle.app.domain.Layout;
import com.processpuzzle.app.domain.NavNode;
import com.processpuzzle.app.domain.Region;
import com.processpuzzle.app.domain.Theme;
import com.processpuzzle.app.domain.Widget;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.model.AppDefinitionStatus;
import com.processpuzzle.app.model.AppDefinitionSummary;
import com.processpuzzle.app.model.AppLayout;
import com.processpuzzle.app.model.ColorScheme;
import com.processpuzzle.app.model.KeyAvailability;
import com.processpuzzle.app.model.LayoutDefinition;
import com.processpuzzle.app.model.LayoutPreset;
import com.processpuzzle.app.model.MaterialTheme;
import com.processpuzzle.app.model.NavItem;
import com.processpuzzle.app.model.PageDefinition;
import com.processpuzzle.app.model.PageOfAppDefinitionSummary;
import com.processpuzzle.app.model.ProvisioningResult;
import com.processpuzzle.app.model.RegionDefinition;
import com.processpuzzle.app.model.RegionType;
import com.processpuzzle.app.model.Severity;
import com.processpuzzle.app.model.SidenavMode;
import com.processpuzzle.app.model.ThemeDefinition;
import com.processpuzzle.app.model.ValidationProblem;
import com.processpuzzle.app.model.ValidationResult;
import com.processpuzzle.app.model.WidgetRef;
import com.processpuzzle.app.usecase.AppValidationProblem;
import com.processpuzzle.app.usecase.ImportOutcome;
import com.processpuzzle.app.usecase.KeyCheckOutcome;
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
                toDomainPages(input.getPages()));
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
                        item.getPageId(),
                        item.getRoles(),
                        toDomainNavItems(item.getChildren())))
                .toList();
    }

    private List<Widget> toDomainWidgets(List<WidgetRef> widgets) {
        if (widgets == null) {
            return List.of();
        }
        return widgets.stream().filter(Objects::nonNull)
                .map(widget -> new Widget(
                        widget.getId(),
                        widget.getType(),
                        widget.getProps(),
                        toDomainWidgets(widget.getChildren())))
                .toList();
    }

    private List<AppPage> toDomainPages(List<PageDefinition> pages) {
        if (pages == null) {
            return List.of();
        }
        return pages.stream().filter(Objects::nonNull)
                .map(page -> new AppPage(
                        page.getId(),
                        page.getTitle(),
                        page.getTranslocoId(),
                        toDomainWidgets(page.getWidgets())))
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
            model.setPages(toModelPages(graph.pages()));
        }
        return model;
    }

    public AppDefinitionSummary toSummary(com.processpuzzle.app.domain.AppDefinition definition) {
        AppDefinitionSummary summary = new AppDefinitionSummary(definition.getId(), definition.getName());
        summary.setOrgKey(definition.getOrgKey());
        summary.setTranslocoId(definition.getTranslocoId());
        summary.setDescription(definition.getDescription());
        summary.setStatus(toModelStatus(definition));
        summary.setVersion(definition.getRevision());
        summary.setPublishedVersion(definition.getPublishedRevision());
        summary.setUpdatedAt(toOffsetDateTime(definition.getUpdatedAt()));
        return summary;
    }

    public PageOfAppDefinitionSummary toModel(Page<com.processpuzzle.app.domain.AppDefinition> page) {
        List<AppDefinitionSummary> content = page.getContent().stream().map(this::toSummary).toList();
        return new PageOfAppDefinitionSummary()
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
            model.setPageId(node.pageId());
            model.setRoles(node.roles());
            model.setChildren(toModelNavItems(node.children()));
            return model;
        }).toList();
    }

    private List<WidgetRef> toModelWidgets(List<Widget> widgets) {
        if (widgets == null) {
            return List.of();
        }
        return widgets.stream().map(widget -> {
            WidgetRef model = new WidgetRef(widget.id(), widget.type());
            model.setProps(widget.props());
            model.setChildren(toModelWidgets(widget.children()));
            return model;
        }).toList();
    }

    private List<PageDefinition> toModelPages(List<AppPage> pages) {
        if (pages == null) {
            return List.of();
        }
        return pages.stream().map(this::toModel).toList();
    }

    public PageDefinition toModel(AppPage page) {
        PageDefinition model = new PageDefinition(page.id(), page.title(), toModelWidgets(page.widgets()));
        model.setTranslocoId(page.translocoId());
        return model;
    }

    // --- organizations -------------------------------------------------------------------

    public com.processpuzzle.app.model.Organization toModel(
            com.processpuzzle.app.domain.Organization organization) {
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

    public ProvisioningResult toModel(com.processpuzzle.app.domain.Organization organization,
                                      com.processpuzzle.app.domain.AppDefinition starterApp) {
        return new ProvisioningResult(toModel(organization), toModel(starterApp));
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
