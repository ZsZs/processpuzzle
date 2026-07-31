package com.processpuzzle.app.usecase.service;

import com.processpuzzle.app.model.AppDefinition;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.model.NavItem;
import com.processpuzzle.app.model.PageDefinition;
import com.processpuzzle.app.model.RegionDefinition;
import com.processpuzzle.app.model.RegionType;
import com.processpuzzle.app.model.ThemeDefinition;
import com.processpuzzle.app.model.WidgetRef;
import com.processpuzzle.app.usecase.AppValidationProblem;
import com.processpuzzle.app.usecase.port.EntityNameRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks the referential integrity of a candidate app definition. Used both by the explicit
 * validate endpoint and by every write, so an invalid definition can never be persisted or
 * published.
 *
 * <p>Deliberately out of scope <em>here</em>:
 *
 * <ul>
 *   <li><b>Enum-valued fields.</b> {@code materialTheme}, {@code colorScheme}, {@code preset},
 *       {@code sidenavMode} and {@code type} are typed enums on the generated input model, so an
 *       invalid value is already rejected by Jackson during deserialization — for the REST path and
 *       the YAML import path alike.
 *   <li><b>Widget {@code props}.</b> Each widget type owns and validates its own props shape on the
 *       frontend; the only thing checked here is the {@code entityName} cross-reference, and only
 *       when an {@link EntityNameRegistry} is available.
 *   <li><b>Conventions and policy.</b> Id shapes, navigation depth, role naming, CSS units,
 *       translatability and anything else a tenant may want to decide for itself are
 *       {@code base-rule} records, evaluated by {@link AppRuleValidator} as part of the same pass —
 *       so tightening them is a database row rather than a change here.
 * </ul>
 *
 * <p>Cycles in the {@code children} trees need no dedicated check: the input arrives as JSON or
 * YAML, which cannot express one. Repeated ids — the observable symptom a designer would hit — are
 * caught by the uniqueness checks.
 *
 * <p>Structural problems are always {@code ERROR}; the organization's rules contribute their own
 * severity, so a returned list may contain problems that do not reject the write — see
 * {@link AppValidationProblem#blocking(List)}.
 */
@Component
public class AppDefinitionValidator {

    private final ObjectProvider<EntityNameRegistry> entityRegistryProvider;
    private final AppRuleValidator ruleValidator;

    public AppDefinitionValidator(ObjectProvider<EntityNameRegistry> entityRegistryProvider,
                                  AppRuleValidator ruleValidator) {
        this.entityRegistryProvider = entityRegistryProvider;
        this.ruleValidator = ruleValidator;
    }

    /**
     * Returns every problem found; no problem of severity {@code ERROR} means the definition may be
     * persisted.
     */
    public List<AppValidationProblem> validate(String orgKey, AppDefinitionInput input) {
        if (input == null) {
            return List.of(new AppValidationProblem("/", "app.validation.missing-body",
                    "No app definition was supplied."));
        }
        List<AppValidationProblem> problems = validateParts(orgKey, input.getId(), input.getName(),
                input.getTheme(), input.getRegions(), input.getPages());
        problems.addAll(ruleValidator.validate(orgKey, input));
        return problems;
    }

    /**
     * Validates an already-persisted definition, used before publishing. The generated
     * {@code AppDefinition} is a standalone class rather than a subtype of {@code AppDefinitionInput}
     * — the generator flattens {@code allOf} — so the shared checks live in {@link #validateParts}.
     *
     * <p>Deliberately named differently instead of overloading {@link #validate}: the two model types
     * are unrelated, so a {@code null} argument would match both and callers would have to cast.
     */
    public List<AppValidationProblem> validateStored(String orgKey, AppDefinition definition) {
        if (definition == null) {
            return List.of(new AppValidationProblem("/", "app.validation.missing-body",
                    "No app definition was supplied."));
        }
        List<AppValidationProblem> problems = validateParts(orgKey, definition.getId(),
                definition.getName(), definition.getTheme(), definition.getRegions(), definition.getPages());
        problems.addAll(ruleValidator.validate(orgKey, definition));
        return problems;
    }

    private List<AppValidationProblem> validateParts(String orgKey, String id, String name,
                                                     ThemeDefinition theme, List<RegionDefinition> regions,
                                                     List<PageDefinition> pages) {
        List<AppValidationProblem> problems = new ArrayList<>();

        if (isBlank(id)) {
            problems.add(new AppValidationProblem("/id", "app.validation.missing-id",
                    "An app definition needs an id; it is used as the route path segment."));
        }
        if (isBlank(name)) {
            problems.add(new AppValidationProblem("/name", "app.validation.missing-name",
                    "An app definition needs a name."));
        }

        validateTheme(theme, problems);
        Set<String> pageIds = validatePages(orgKey, pages, problems);
        Set<String> referencedPageIds = validateRegions(orgKey, regions, pageIds, problems);
        reportOrphanPages(pages, referencedPageIds, problems);

        return problems;
    }

    private void validateTheme(ThemeDefinition theme, List<AppValidationProblem> problems) {
        if (theme == null || theme.getTokenOverrides() == null) {
            return;
        }
        for (Map.Entry<String, String> override : theme.getTokenOverrides().entrySet()) {
            if (!PpThemeTokens.isKnown(override.getKey())) {
                problems.add(new AppValidationProblem(
                        "/theme/tokenOverrides/" + override.getKey(),
                        "app.validation.unknown-theme-token",
                        "'" + override.getKey() + "' is not a ProcessPuzzle theme token. "
                                + "Known tokens: " + String.join(", ", sorted(PpThemeTokens.names())) + "."));
            }
        }
    }

    private Set<String> validatePages(String orgKey, List<PageDefinition> pages,
                                      List<AppValidationProblem> problems) {
        Set<String> pageIds = new LinkedHashSet<>();
        if (pages == null) {
            return pageIds;
        }
        for (int i = 0; i < pages.size(); i++) {
            PageDefinition page = pages.get(i);
            String path = "/pages/" + i;
            if (page == null) {
                problems.add(new AppValidationProblem(path, "app.validation.null-page", "A page entry is null."));
                continue;
            }
            if (isBlank(page.getId())) {
                problems.add(new AppValidationProblem(path + "/id", "app.validation.missing-page-id",
                        "A page needs an id; it is used as the route path segment."));
            } else if (!pageIds.add(page.getId())) {
                problems.add(new AppValidationProblem(path + "/id", "app.validation.duplicate-page-id",
                        "More than one page uses the id '" + page.getId() + "'."));
            }
            if (isBlank(page.getTitle())) {
                problems.add(new AppValidationProblem(path + "/title", "app.validation.missing-page-title",
                        "A page needs a title."));
            }
            validateWidgets(orgKey, page.getWidgets(), path + "/widgets", new HashSet<>(), problems);
        }
        return pageIds;
    }

    private void validateWidgets(String orgKey, List<WidgetRef> widgets, String basePath,
                                 Set<String> seenIds, List<AppValidationProblem> problems) {
        if (widgets == null) {
            return;
        }
        for (int i = 0; i < widgets.size(); i++) {
            WidgetRef widget = widgets.get(i);
            String path = basePath + "/" + i;
            if (widget == null) {
                problems.add(new AppValidationProblem(path, "app.validation.null-widget", "A widget entry is null."));
                continue;
            }
            if (isBlank(widget.getId())) {
                problems.add(new AppValidationProblem(path + "/id", "app.validation.missing-widget-id",
                        "A widget needs an id."));
            } else if (!seenIds.add(widget.getId())) {
                problems.add(new AppValidationProblem(path + "/id", "app.validation.duplicate-widget-id",
                        "More than one widget in this page or region uses the id '" + widget.getId() + "'."));
            }
            if (isBlank(widget.getType())) {
                problems.add(new AppValidationProblem(path + "/type", "app.validation.missing-widget-type",
                        "A widget needs a type; it is the key into the frontend widget registry."));
            }
            validateEntityName(orgKey, widget, path, problems);
            validateWidgets(orgKey, widget.getChildren(), path + "/children", seenIds, problems);
        }
    }

    private void validateEntityName(String orgKey, WidgetRef widget, String path,
                                    List<AppValidationProblem> problems) {
        Map<String, Object> props = widget.getProps();
        if (props == null) {
            return;
        }
        Object entityName = props.get("entityName");
        if (!(entityName instanceof String name) || name.isBlank()) {
            return;
        }
        EntityNameRegistry registry = entityRegistryProvider.getIfAvailable();
        if (registry != null && !registry.isKnownEntity(orgKey, name)) {
            problems.add(new AppValidationProblem(path + "/props/entityName",
                    "app.validation.unknown-entity-name",
                    "No entity descriptor named '" + name + "' is registered for this organization."));
        }
    }

    private Set<String> validateRegions(String orgKey, List<RegionDefinition> regions,
                                        Set<String> pageIds, List<AppValidationProblem> problems) {
        Set<String> referencedPageIds = new LinkedHashSet<>();
        if (regions == null) {
            return referencedPageIds;
        }
        Set<RegionType> seenTypes = new HashSet<>();
        Set<String> navIds = new HashSet<>();
        for (int i = 0; i < regions.size(); i++) {
            RegionDefinition region = regions.get(i);
            String path = "/regions/" + i;
            if (region == null) {
                problems.add(new AppValidationProblem(path, "app.validation.null-region", "A region entry is null."));
                continue;
            }
            if (region.getType() == null) {
                problems.add(new AppValidationProblem(path + "/type", "app.validation.missing-region-type",
                        "A region needs a type."));
                continue;
            }
            if (!seenTypes.add(region.getType())) {
                problems.add(new AppValidationProblem(path + "/type", "app.validation.duplicate-region",
                        "More than one '" + region.getType().getValue() + "' region is declared."));
            }
            validateRegionContents(orgKey, region, path, pageIds, navIds, referencedPageIds, problems);
        }
        return referencedPageIds;
    }

    private void validateRegionContents(String orgKey, RegionDefinition region, String path,
                                        Set<String> pageIds, Set<String> navIds,
                                        Set<String> referencedPageIds, List<AppValidationProblem> problems) {
        boolean isSidenav = region.getType() == RegionType.SIDENAV;
        boolean hasNavItems = region.getNavItems() != null && !region.getNavItems().isEmpty();
        boolean hasWidgets = region.getWidgets() != null && !region.getWidgets().isEmpty();

        if (hasNavItems && !isSidenav) {
            problems.add(new AppValidationProblem(path + "/navItems", "app.validation.nav-items-not-allowed",
                    "Only a sidenav region carries navItems; the '" + region.getType().getValue()
                            + "' region declares " + region.getNavItems().size() + "."));
        }
        boolean staticContentAllowed = region.getType() == RegionType.HEADER || region.getType() == RegionType.FOOTER;
        if (hasWidgets && !staticContentAllowed) {
            problems.add(new AppValidationProblem(path + "/widgets", "app.validation.widgets-not-allowed",
                    "Only a header or footer region carries static widgets; the '"
                            + region.getType().getValue() + "' region declares "
                            + region.getWidgets().size() + ". Content-region widgets belong to a page."));
        }
        if (isSidenav) {
            validateNavItems(region.getNavItems(), path + "/navItems", pageIds, navIds, referencedPageIds, problems);
        }
        if (staticContentAllowed) {
            validateWidgets(orgKey, region.getWidgets(), path + "/widgets", new HashSet<>(), problems);
        }
    }

    private void validateNavItems(List<NavItem> navItems, String basePath, Set<String> pageIds,
                                  Set<String> navIds, Set<String> referencedPageIds,
                                  List<AppValidationProblem> problems) {
        if (navItems == null) {
            return;
        }
        for (int i = 0; i < navItems.size(); i++) {
            NavItem item = navItems.get(i);
            String path = basePath + "/" + i;
            if (item == null) {
                problems.add(new AppValidationProblem(path, "app.validation.null-nav-item",
                        "A nav item entry is null."));
                continue;
            }
            if (isBlank(item.getId())) {
                problems.add(new AppValidationProblem(path + "/id", "app.validation.missing-nav-item-id",
                        "A nav item needs an id."));
            } else if (!navIds.add(item.getId())) {
                problems.add(new AppValidationProblem(path + "/id", "app.validation.duplicate-nav-item-id",
                        "More than one nav item uses the id '" + item.getId() + "'."));
            }
            if (isBlank(item.getLabel())) {
                problems.add(new AppValidationProblem(path + "/label", "app.validation.missing-nav-item-label",
                        "A nav item needs a label."));
            }

            boolean hasChildren = item.getChildren() != null && !item.getChildren().isEmpty();
            if (isBlank(item.getPageId())) {
                if (!hasChildren) {
                    problems.add(new AppValidationProblem(path, "app.validation.dead-nav-item",
                            "Nav item '" + item.getId() + "' has neither a pageId nor children, "
                                    + "so it would render as an entry that does nothing."));
                }
            } else if (!pageIds.contains(item.getPageId())) {
                problems.add(new AppValidationProblem(path + "/pageId", "app.validation.unknown-page-reference",
                        "No page with id '" + item.getPageId() + "' is declared in this app definition."));
            } else {
                referencedPageIds.add(item.getPageId());
            }

            validateNavItems(item.getChildren(), path + "/children", pageIds, navIds, referencedPageIds, problems);
        }
    }

    private void reportOrphanPages(List<PageDefinition> pages, Set<String> referencedPageIds,
                                   List<AppValidationProblem> problems) {
        if (pages == null) {
            return;
        }
        for (int i = 0; i < pages.size(); i++) {
            PageDefinition page = pages.get(i);
            if (page == null || isBlank(page.getId())) {
                continue;
            }
            if (!referencedPageIds.contains(page.getId())) {
                problems.add(new AppValidationProblem("/pages/" + i, "app.validation.orphan-page",
                        "Page '" + page.getId() + "' is not reachable: no nav item references it."));
            }
        }
    }

    private static List<String> sorted(Set<String> values) {
        return values.stream().sorted().toList();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
