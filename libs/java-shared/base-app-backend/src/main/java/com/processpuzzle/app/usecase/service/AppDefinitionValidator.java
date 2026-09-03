package com.processpuzzle.app.usecase.service;

import com.processpuzzle.app.model.AppDefinition;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.model.ModuleDefinitionInput;
import com.processpuzzle.app.model.ModuleMount;
import com.processpuzzle.app.model.NavItem;
import com.processpuzzle.app.model.RouteDefinition;
import com.processpuzzle.app.model.RegionDefinition;
import com.processpuzzle.app.model.RegionType;
import com.processpuzzle.app.model.ThemeDefinition;
import com.processpuzzle.shared.model.WidgetInstance;
import com.processpuzzle.app.usecase.AppValidationProblem;
import com.processpuzzle.app.usecase.port.EntityNameRegistry;
import com.processpuzzle.app.usecase.Severity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 *       frontend, including how it interprets {@code childIds} beyond "these ids must resolve". The
 *       only other thing checked here is the {@code entityName} cross-reference, and only when an
 *       {@link EntityNameRegistry} is available.
 *   <li><b>Conventions and policy.</b> Id shapes, navigation depth, role naming, CSS units,
 *       translatability and anything else a tenant may want to decide for itself are
 *       {@code base-rule} records, evaluated by {@link AppRuleValidator} as part of the same pass —
 *       so tightening them is a database row rather than a change here.
 * </ul>
 *
 * <p>Cycles in the {@code navItems} tree need no dedicated check: the input arrives as JSON or
 * YAML, which cannot express one. Repeated ids — the observable symptom a designer would hit — are
 * caught by the uniqueness checks. Widgets no longer nest, so no widget tree can cycle
 * structurally either — but {@code props.childIds} can now express a cycle between two
 * {@code REFERENCED} widgets, and the contract deliberately leaves that unchecked here: what a
 * container widget does with its {@code childIds} is that widget type's own concern, and only it
 * knows whether repeating an id is a loop or a legitimate second placement.
 *
 * <p>Structural problems are always {@code ERROR}; the organization's rules contribute their own
 * severity, so a returned list may contain problems that do not reject the write — see
 * {@link AppValidationProblem#blocking(List)}.
 */
@Component
public class AppDefinitionValidator {

    private static final String SEPARATOR = "/";
    private static final String WIDGETS = "/widgets";
    private static final String TYPE = "/type";
    private static final String CHILD_IDS = "childIds";

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
                input.getTheme(), input.getRegions(), input.getRoutes(), input.getModules());
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
                definition.getName(), definition.getTheme(), definition.getRegions(), definition.getRoutes(), definition.getModules());
        problems.addAll(ruleValidator.validate(orgKey, definition));
        return problems;
    }

    /**
     * A module's own integrity: it needs a key and a name, and its routes are checked exactly like an
     * app's — the same {@link RouteDefinition} list, so the same duplicate-path and widget rules.
     *
     * <p>Two app-level checks deliberately do not apply. A module carries no regions, so there is no
     * nav tree to resolve route paths against and therefore no orphan-route warning: a module's routes
     * are reached through the mounting app's navigation, which this aggregate cannot see. And a module
     * mounts no modules, so there are no basePath collisions to look for.
     */
    public List<AppValidationProblem> validateModule(String orgKey, ModuleDefinitionInput input) {
        if (input == null) {
            return List.of(new AppValidationProblem("/", "app.validation.missing-body",
                    "No module definition was supplied."));
        }
        List<AppValidationProblem> problems = new ArrayList<>();
        if (isBlank(input.getKey())) {
            problems.add(new AppValidationProblem("/key", "module.validation.missing-key",
                    "A module needs a key; it is what an AppDefinition mount references."));
        }
        if (isBlank(input.getName())) {
            problems.add(new AppValidationProblem("/name", "module.validation.missing-name",
                    "A module needs a name."));
        }
        validateRoutes(orgKey, input.getRoutes(), problems);
        return problems;
    }

    private List<AppValidationProblem> validateParts(String orgKey, String id, String name,
                                                     ThemeDefinition theme, List<RegionDefinition> regions,
                                                     List<RouteDefinition> routes, List<ModuleMount> modules) {
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
        Set<String> routePaths = validateRoutes(orgKey, routes, problems);
        validateModuleMounts(modules, routePaths, problems);
        Set<String> referencedRoutePaths = validateRegions(orgKey, regions, routePaths, problems);
        reportOrphanRoutes(routes, referencedRoutePaths, problems);

        return problems;
    }

    /**
     * Module mounts. Two collisions block the write, because either one makes routing ambiguous:
     * a basePath used by two mounts, and a basePath that an app-level route already occupies.
     *
     * <p>A {@code moduleKey} naming no existing module is deliberately <em>not</em> checked here.
     * Modules are separate aggregates and loosely coupled by design — an app may legitimately mount
     * one that has not been authored yet — and resolving the key would make this validator depend on
     * the module repository, coupling the two after all.
     */
    private void validateModuleMounts(List<ModuleMount> modules, Set<String> routePaths,
                                      List<AppValidationProblem> problems) {
        if (modules == null) {
            return;
        }
        Set<String> basePaths = new LinkedHashSet<>();
        for (int i = 0; i < modules.size(); i++) {
            String path = "/modules/" + i;
            ModuleMount mount = modules.get(i);
            if (mount == null) {
                problems.add(new AppValidationProblem(path, "app.validation.null-module-mount",
                        "A module mount entry is null."));
                continue;
            }
            if (isBlank(mount.getModuleKey())) {
                problems.add(new AppValidationProblem(path + "/moduleKey", "app.validation.missing-module-key",
                        "A module mount needs a moduleKey."));
            }
            if (isBlank(mount.getBasePath())) {
                problems.add(new AppValidationProblem(path + "/basePath", "app.validation.missing-base-path",
                        "A module mount needs a basePath."));
                continue;
            }
            if (!basePaths.add(mount.getBasePath())) {
                problems.add(new AppValidationProblem(path + "/basePath", "app.validation.duplicate-base-path",
                        "More than one module is mounted at '" + mount.getBasePath() + "'."));
            }
            if (routePaths.contains(mount.getBasePath())) {
                problems.add(new AppValidationProblem(path + "/basePath", "app.validation.base-path-collides-with-route",
                        "A route already occupies '" + mount.getBasePath() + "', so the module mounted "
                                + "there could never be reached."));
            }
        }
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

    private Set<String> validateRoutes(String orgKey, List<RouteDefinition> routes,
                                      List<AppValidationProblem> problems) {
        Set<String> routePaths = new LinkedHashSet<>();
        if (routes == null) {
            return routePaths;
        }
        for (int i = 0; i < routes.size(); i++) {
            RouteDefinition route = routes.get(i);
            String path = "/routes/" + i;
            if (route == null) {
                problems.add(new AppValidationProblem(path, "app.validation.null-route", "A route entry is null."));
                continue;
            }
            if (isBlank(route.getPath())) {
                problems.add(new AppValidationProblem(path + "/path", "app.validation.missing-route-path",
                        "A route needs a path."));
            } else if (!routePaths.add(route.getPath())) {
                problems.add(new AppValidationProblem(path + "/path", "app.validation.duplicate-route-path",
                        "More than one route uses the path '" + route.getPath() + "'."));
            }
            if (isBlank(route.getTitle())) {
                problems.add(new AppValidationProblem(path + "/title", "app.validation.missing-route-title",
                        "A route needs a title."));
            }
            // Widgets live on the target now, and only a WIDGETS target has any.
            if (route.getTarget() != null) {
                validateWidgets(orgKey, route.getTarget().getWidgets(), path + "/target" + WIDGETS, problems);
            }
        }
        return routePaths;
    }

    /**
     * The widgets of one route or region. The list is flat — container widget types compose through
     * {@code props.childIds} rather than nesting — so the id scope of a {@code childIds} entry is
     * exactly this list, and both passes below can work off one index.
     */
    private void validateWidgets(String orgKey, List<WidgetInstance> widgets, String basePath,
                                 List<AppValidationProblem> problems) {
        if (widgets == null) {
            return;
        }
        Map<String, WidgetInstance> byId = new LinkedHashMap<>();
        for (int i = 0; i < widgets.size(); i++) {
            WidgetInstance widget = widgets.get(i);
            String path = basePath + SEPARATOR + i;
            if (widget == null) {
                problems.add(new AppValidationProblem(path, "app.validation.null-widget", "A widget entry is null."));
                continue;
            }
            if (isBlank(widget.getId())) {
                problems.add(new AppValidationProblem(path + "/id", "app.validation.missing-widget-id",
                        "A widget needs an id."));
            } else if (byId.putIfAbsent(widget.getId(), widget) != null) {
                problems.add(new AppValidationProblem(path + "/id", "app.validation.duplicate-widget-id",
                        "More than one widget in this route or region uses the id '" + widget.getId() + "'."));
            }
            if (isBlank(widget.getType())) {
                problems.add(new AppValidationProblem(path + TYPE, "app.validation.missing-widget-type",
                        "A widget needs a type; it is the key into the frontend widget registry."));
            }
            validateEntityName(orgKey, widget, path, problems);
        }
        validateComposition(widgets, basePath, byId, problems);
    }

    /**
     * The two checks that replace the old nesting: every {@code props.childIds} entry naming a
     * sibling that is actually available to be placed, and every widget that opted out of rendering
     * at its own position having something that places it.
     *
     * <p>The orphan case is a {@code WARNING} rather than an {@code ERROR} on purpose — declaring a
     * widget before wiring it into its container is a legitimate state for a draft to be saved in.
     */
    private void validateComposition(List<WidgetInstance> widgets, String basePath,
                                     Map<String, WidgetInstance> byId, List<AppValidationProblem> problems) {
        Set<String> referencedIds = new HashSet<>();
        for (int i = 0; i < widgets.size(); i++) {
            WidgetInstance widget = widgets.get(i);
            if (widget == null) {
                continue;
            }
            for (String childId : childIdsOf(widget)) {
                WidgetInstance target = byId.get(childId);
                if (target == null || target.getPlacement() != WidgetInstance.PlacementEnum.REFERENCED) {
                    problems.add(new AppValidationProblem(
                            basePath + SEPARATOR + i + "/props/" + CHILD_IDS,
                            "app.validation.dangling-child-id",
                            "props.childIds references widget '" + childId + "', which is not a widget with "
                                    + "placement REFERENCED in this route or region."));
                } else {
                    referencedIds.add(childId);
                }
            }
        }
        reportOrphanWidgets(widgets, basePath, referencedIds, problems);
    }

    private void reportOrphanWidgets(List<WidgetInstance> widgets, String basePath, Set<String> referencedIds,
                                     List<AppValidationProblem> problems) {
        for (int i = 0; i < widgets.size(); i++) {
            WidgetInstance widget = widgets.get(i);
            if (widget == null || widget.getPlacement() != WidgetInstance.PlacementEnum.REFERENCED) {
                continue;
            }
            if (!referencedIds.contains(widget.getId())) {
                problems.add(new AppValidationProblem(basePath + SEPARATOR + i, "app.validation.orphan-widget",
                        "Widget '" + widget.getId() + "' is REFERENCED but nothing points at it yet, "
                                + "so it would not be rendered.", Severity.WARNING));
            }
        }
    }

    /**
     * {@code childIds} is read out of the open {@code props} map rather than being a field of its
     * own: composition is one widget type's convention, and the contract keeps {@code props} loose.
     * Anything that is not a list of strings is left to the widget type to reject.
     */
    private List<String> childIdsOf(WidgetInstance widget) {
        Map<String, Object> props = widget.getProps();
        if (props == null || !(props.get(CHILD_IDS) instanceof List<?> raw)) {
            return List.of();
        }
        return raw.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private void validateEntityName(String orgKey, WidgetInstance widget, String path,
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
                                        Set<String> routePaths, List<AppValidationProblem> problems) {
        Set<String> referencedRoutePaths = new LinkedHashSet<>();
        if (regions == null) {
            return referencedRoutePaths;
        }
        Set<RegionType> seenTypes = new HashSet<>();
        Set<String> navIds = new HashSet<>();
        for (int i = 0; i < regions.size(); i++) {
            RegionDefinition region = regions.get(i);
            String path = "/regions/" + i;
            if (region == null) {
                problems.add(new AppValidationProblem(path, "app.validation.null-region", "A region entry is null."));
            } else if (region.getType() == null) {
                problems.add(new AppValidationProblem(path + TYPE, "app.validation.missing-region-type",
                        "A region needs a type."));
            } else {
                if (!seenTypes.add(region.getType())) {
                    problems.add(new AppValidationProblem(path + TYPE, "app.validation.duplicate-region",
                            "More than one '" + region.getType().getValue() + "' region is declared."));
                }
                validateRegionContents(orgKey, region, path, routePaths, navIds, referencedRoutePaths, problems);
            }
        }
        return referencedRoutePaths;
    }

    private void validateRegionContents(String orgKey, RegionDefinition region, String path,
                                        Set<String> routePaths, Set<String> navIds,
                                        Set<String> referencedRoutePaths, List<AppValidationProblem> problems) {
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
            problems.add(new AppValidationProblem(path + WIDGETS, "app.validation.widgets-not-allowed",
                    "Only a header or footer region carries static widgets; the '"
                            + region.getType().getValue() + "' region declares "
                            + region.getWidgets().size() + ". Content-region widgets belong to a route."));
        }
        if (isSidenav) {
            validateNavItems(region.getNavItems(), path + "/navItems", routePaths, navIds, referencedRoutePaths, problems);
        }
        if (staticContentAllowed) {
            validateWidgets(orgKey, region.getWidgets(), path + WIDGETS, problems);
        }
    }

    private void validateNavItems(List<NavItem> navItems, String basePath, Set<String> routePaths,
                                  Set<String> navIds, Set<String> referencedRoutePaths,
                                  List<AppValidationProblem> problems) {
        if (navItems == null) {
            return;
        }
        for (int i = 0; i < navItems.size(); i++) {
            NavItem item = navItems.get(i);
            String path = basePath + SEPARATOR + i;
            if (item == null) {
                problems.add(new AppValidationProblem(path, "app.validation.null-nav-item",
                        "A nav item entry is null."));
            } else {
                validateNavItem(item, path, navIds, problems);
                validateRouteReference(item, path, routePaths, referencedRoutePaths, problems);
                validateNavItems(item.getChildren(), path + "/children", routePaths, navIds, referencedRoutePaths,
                        problems);
            }
        }
    }

    private void validateNavItem(NavItem item, String path, Set<String> navIds,
                                 List<AppValidationProblem> problems) {
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
    }

    private void validateRouteReference(NavItem item, String path, Set<String> routePaths,
                                       Set<String> referencedRoutePaths, List<AppValidationProblem> problems) {
        boolean hasChildren = item.getChildren() != null && !item.getChildren().isEmpty();
        if (isBlank(item.getRoutePath())) {
            if (!hasChildren) {
                problems.add(new AppValidationProblem(path, "app.validation.dead-nav-item",
                        "Nav item '" + item.getId() + "' has neither a routePath nor children, "
                                + "so it would render as an entry that does nothing."));
            }
        } else if (!routePaths.contains(item.getRoutePath())) {
            // WARNING, not ERROR: a nav item may legitimately point into a mounted module whose
            // routes are not part of this aggregate, or at a module not yet authored. Blocking the
            // write here would make modules tightly coupled after all.
            problems.add(new AppValidationProblem(path + "/routePath", "app.validation.unknown-route-reference",
                    "No route with path '" + item.getRoutePath() + "' is declared in this app definition; "
                            + "it may belong to a mounted module.", Severity.WARNING));
        } else {
            referencedRoutePaths.add(item.getRoutePath());
        }
    }

    private void reportOrphanRoutes(List<RouteDefinition> routes, Set<String> referencedRoutePaths,
                                   List<AppValidationProblem> problems) {
        if (routes == null) {
            return;
        }
        for (int i = 0; i < routes.size(); i++) {
            RouteDefinition route = routes.get(i);
            if (route == null || isBlank(route.getPath())) {
                continue;
            }
            if (!referencedRoutePaths.contains(route.getPath())) {
                // Also a WARNING: a flat route is addressable by URL whether or not the sidenav
                // links it, so an unreferenced route is a navigation gap rather than broken data.
                problems.add(new AppValidationProblem("/routes/" + i, "app.validation.orphan-route",
                        "Route '" + route.getPath() + "' is not reachable from the navigation tree.",
                        Severity.WARNING));
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
