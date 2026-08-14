import { BaseEntity } from '@processpuzzle/base-entity';
import { WidgetInstance } from '@processpuzzle/base-widget';

export { WIDGET_PLACEMENTS, WidgetInstance, WidgetPlacement } from '@processpuzzle/base-widget';

/**
 * Frontend model of the `AppDefinition` schema of `base-app-api.yaml`. `theme` and `layout` are
 * flattened onto the entity by {@link AppDefinitionMapper} so that the generated form can offer a
 * typed control per field; the original objects are kept alongside, because a full replacement PUT
 * would otherwise drop the parts no control writes. `regions`, `routes` and `modules` stay nested and
 * are edited through the `EMBEDDED_COMPONENTS` controls of the `App Region` / `App Route` /
 * `App Module Mount` descriptors.
 *
 * A route's `target` is flattened the same way, for the same reason and by the same mapper. The
 * contract already keeps `RouteTarget` flat rather than a `oneOf` because the generic form cannot edit
 * a discriminated union of classes; a nested object it equally cannot reach, since an
 * `EMBEDDED_COMPONENTS` row is edited as the parsed JSON it arrived as and a descriptor addresses one
 * property, not a path. So {@link RouteDefinition} carries `kind` and the per-kind fields directly and
 * {@link AppDefinitionMapper} re-nests them on save.
 *
 * The nested definitions are classes rather than interfaces, because each is an embedded entity of
 * its own: `EmbeddedEntityFacade` mints the blank row an `Add` opens the child's form on, and that
 * needs a constructor. They stay plain data — the rows of a loaded aggregate are the parsed JSON,
 * never instances of these classes, so nothing may rely on `instanceof` or on a method.
 */

export const MATERIAL_THEMES = ['azure-blue', 'rose-red', 'magenta-violet', 'cyan-orange'] as const;
export type MaterialTheme = (typeof MATERIAL_THEMES)[number];

export const COLOR_SCHEMES = ['light', 'dark', 'auto'] as const;
export type ColorScheme = (typeof COLOR_SCHEMES)[number];

export const LAYOUT_PRESETS = ['sidenav-left', 'sidenav-right', 'top-nav'] as const;
export type LayoutPreset = (typeof LAYOUT_PRESETS)[number];

export const SIDENAV_MODES = ['side', 'over', 'push'] as const;
export type SidenavMode = (typeof SIDENAV_MODES)[number];

/**
 * No `content`: once routes own the content area it *is* the router outlet, so a content region would
 * have no field of its own — `widgets` is header/footer-only and `navItems` sidenav-only. Its sizing
 * lives in {@link LayoutDefinition.contentMaxWidth}.
 */
export const REGION_TYPES = ['header', 'sidenav', 'footer'] as const;
export type RegionType = (typeof REGION_TYPES)[number];

/**
 * What a route renders. Uppercase because the contract's enum is — `RegionType` and the theme enums
 * are lowercase, and mixing the two up is a silent miss rather than a type error once a value reaches
 * JSON.
 */
export const ROUTE_TARGET_KINDS = ['WIDGETS', 'DOCUMENT', 'ENTITY'] as const;
export type RouteTargetKind = (typeof ROUTE_TARGET_KINDS)[number];

export const ENTITY_MODES = ['LIST', 'DETAILS'] as const;
export type EntityMode = (typeof ENTITY_MODES)[number];

export enum AppDefinitionStatus {
  DRAFT = 'DRAFT',
  PUBLISHED = 'PUBLISHED',
}

export interface ThemeDefinition {
  materialTheme?: MaterialTheme;
  colorScheme?: ColorScheme;
  /** Overrides for the `--pp-*` custom properties of `widgets/src/theme/pp-colors.css`. */
  tokenOverrides?: Record<string, string>;
  logoUrl?: string;
  faviconUrl?: string;
}

export interface LayoutDefinition {
  preset?: LayoutPreset;
  sidenavMode?: SidenavMode;
  sidenavCollapsible?: boolean;
  sidenavOpenByDefault?: boolean;
  contentMaxWidth?: string;
}

export class NavItem implements BaseEntity {
  id: string;
  label: string;
  translocoId?: string;
  icon?: string;
  /**
   * `RouteDefinition.path` of the route this entry navigates to, resolved by string. Absent on a group
   * node, which expands its {@link children} instead of navigating.
   *
   * A path naming no route is a validation *warning* server-side, not an error: it may name a route of
   * a module that has not been authored yet, and modules stay loosely coupled.
   */
  routePath?: string;
  roles?: string[];
  children?: NavItem[];

  constructor(init: Partial<NavItem> = {}) {
    this.id = init.id ?? '';
    this.label = init.label ?? '';
    this.translocoId = init.translocoId;
    this.icon = init.icon;
    this.routePath = init.routePath;
    this.roles = init.roles;
    this.children = init.children;
  }
}

export class RegionDefinition implements BaseEntity {
  /**
   * Declared, never assigned. The contract gives a region no `id` — `type` identifies it, see
   * `APP_REGION_ID_FIELD` — but `BaseEntity`'s only property is an optional `id`, and TypeScript's
   * weak-type rule rejects a type that shares no property with it. `declare` emits nothing, so the
   * payload stays exactly the shape the schema describes.
   */
  declare readonly id?: string;

  /**
   * Required by the contract, but undefined while a region is being created — the dropdown is declared
   * `required`, so the form is what enforces the contract before the row reaches the payload.
   */
  type: RegionType | undefined;
  /** `sidenav` only; left undefined so a header region's payload carries no empty nav tree. */
  navItems?: NavItem[];
  /** `header` / `footer` only. */
  widgets?: WidgetInstance[];

  constructor(init: Partial<RegionDefinition> = {}) {
    this.type = init.type;
    this.navItems = init.navItems;
    this.widgets = init.widgets;
  }
}

/**
 * The nested `target` object of the DTO. Only {@link AppDefinitionMapper} handles it — the form works
 * on the fields {@link RouteDefinition} carries flattened — so it is an interface rather than a class.
 */
export interface RouteTarget {
  kind: RouteTargetKind;
  /** WIDGETS only. */
  widgets?: WidgetInstance[];
  /** DOCUMENT only. */
  documentSlug?: string;
  /** ENTITY only. */
  entityName?: string;
  /** ENTITY only. */
  entityMode?: EntityMode;
  /** ENTITY + LIST only. */
  rsqlFilter?: string;
}

/**
 * One navigable route. **Flat: a route has no children.** `path` may be multi-segment, so `claims`,
 * `claims/open` and `claims/:id` are three sibling entries rather than a tree — Angular's own nesting
 * is derived from those prefixes when the shell builds its `Routes`, because nesting is a rendering
 * concern rather than something worth authoring. Structure is broken up at the module boundary
 * instead, through {@link ModuleMount}.
 *
 * The `target` fields are flattened onto the row and re-nested by {@link AppDefinitionMapper}; which of
 * them are meaningful follows from {@link kind}, and the backend — not the form — is what enforces it.
 */
export class RouteDefinition implements BaseEntity {
  /**
   * Declared, never assigned. The contract gives a route no `id`: `path` identifies it, which is what
   * a `NavItem.routePath` resolves against. Same reason as {@link RegionDefinition.id}.
   */
  declare readonly id?: string;

  /** Relative to the app root, no leading slash. May contain `/` for depth and `:name` for a parameter. */
  path: string;
  title: string;
  translocoId?: string;
  icon?: string;
  /** Empty or absent means any authenticated member of the organization, as on {@link NavItem.roles}. */
  roles?: string[];
  // region flattened target — re-nested into `target` by AppDefinitionMapper
  /**
   * Required by the contract, but undefined while a route is being created — the dropdown is declared
   * `required`, so the form is what enforces it before the row reaches the payload.
   */
  kind: RouteTargetKind | undefined;
  /** WIDGETS only. Starts as an empty array so the embedded list has something to append to. */
  widgets: WidgetInstance[];
  documentSlug?: string;
  entityName?: string;
  entityMode?: EntityMode;
  rsqlFilter?: string;
  // endregion
  /** The object the flattened fields came from, preserved so a save cannot drop a future field. */
  target?: RouteTarget;

  constructor(init: Partial<RouteDefinition> = {}) {
    this.path = init.path ?? '';
    this.title = init.title ?? '';
    this.translocoId = init.translocoId;
    this.icon = init.icon;
    this.roles = init.roles;
    this.kind = init.kind;
    this.widgets = init.widgets ?? [];
    this.documentSlug = init.documentSlug;
    this.entityName = init.entityName;
    this.entityMode = init.entityMode;
    this.rsqlFilter = init.rsqlFilter;
    this.target = init.target;
  }
}

/**
 * Mounts a module's routes under {@link basePath}. The one place route structure composes, and it
 * composes exactly one level deep: a module cannot mount modules.
 */
export class ModuleMount implements BaseEntity {
  /** `moduleKey` identifies the mount; the contract gives it no `id`. */
  declare readonly id?: string;

  /**
   * Key of a `ModuleDefinition` in this organization. One naming no existing module is a validation
   * warning, not an error — an app may mount a module before it has been authored.
   */
  moduleKey: string;
  basePath: string;

  constructor(init: Partial<ModuleMount> = {}) {
    this.moduleKey = init.moduleKey ?? '';
    this.basePath = init.basePath ?? '';
  }
}

export class AppDefinition implements BaseEntity {
  /** Unique within the organization and used verbatim as the run-time route segment. */
  id: string;
  name: string;
  translocoId: string | undefined;
  description: string | undefined;
  // region flattened theme
  materialTheme: MaterialTheme | undefined;
  colorScheme: ColorScheme | undefined;
  tokenOverrides: Record<string, string> | undefined;
  logoUrl: string | undefined;
  faviconUrl: string | undefined;
  // endregion
  // region flattened layout
  preset: LayoutPreset | undefined;
  sidenavMode: SidenavMode | undefined;
  sidenavCollapsible: boolean;
  sidenavOpenByDefault: boolean;
  contentMaxWidth: string | undefined;
  // endregion
  /** The objects the flattened fields came from, preserved so a save cannot drop a future field. */
  theme: ThemeDefinition | undefined;
  layout: LayoutDefinition | undefined;
  regions: RegionDefinition[] | undefined;
  routes: RouteDefinition[] | undefined;
  modules: ModuleMount[] | undefined;
  // region server-assigned
  orgKey: string | undefined;
  status: AppDefinitionStatus | undefined;
  version: number | undefined;
  publishedVersion: number | undefined;
  createdAt: string | undefined;
  updatedAt: string | undefined;
  // endregion

  constructor(init: Partial<AppDefinition> = {}) {
    this.id = init.id ?? '';
    this.name = init.name ?? '';
    this.translocoId = init.translocoId;
    this.description = init.description;
    this.materialTheme = init.materialTheme;
    this.colorScheme = init.colorScheme;
    this.tokenOverrides = init.tokenOverrides;
    this.logoUrl = init.logoUrl;
    this.faviconUrl = init.faviconUrl;
    this.preset = init.preset;
    this.sidenavMode = init.sidenavMode;
    this.sidenavCollapsible = init.sidenavCollapsible ?? true;
    this.sidenavOpenByDefault = init.sidenavOpenByDefault ?? true;
    this.contentMaxWidth = init.contentMaxWidth;
    this.theme = init.theme;
    this.layout = init.layout;
    this.regions = init.regions;
    this.routes = init.routes;
    this.modules = init.modules;
    this.orgKey = init.orgKey;
    this.status = init.status;
    this.version = init.version;
    this.publishedVersion = init.publishedVersion;
    this.createdAt = init.createdAt;
    this.updatedAt = init.updatedAt;
  }
}
