import { BaseEntity } from '@processpuzzle/base-entity';

/**
 * Frontend model of the `AppDefinition` schema of `base-app-api.yaml`. `theme` and `layout` are
 * flattened onto the entity by {@link AppDefinitionMapper} so that the generated form can offer a
 * typed control per field; the original objects are kept alongside, because a full replacement PUT
 * would otherwise drop the parts no control writes. `regions` and `pages` stay nested and are edited
 * through the `EMBEDDED_COMPONENTS` controls of the `App Region` / `App Page` descriptors.
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

export const REGION_TYPES = ['header', 'sidenav', 'content', 'footer'] as const;
export type RegionType = (typeof REGION_TYPES)[number];

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

export class WidgetRef implements BaseEntity {
  /** Authored, not generated: it is the trackBy key of the render loop and unique within its owner. */
  id: string;
  /** Widget registry key, an open string by contract. */
  type: string;
  props?: Record<string, unknown>;
  /** Left undefined rather than empty, so a leaf widget's payload stays the leaf the schema describes. */
  children?: WidgetRef[];

  constructor(init: Partial<WidgetRef> = {}) {
    this.id = init.id ?? '';
    this.type = init.type ?? '';
    this.props = init.props;
    this.children = init.children;
  }
}

export class NavItem implements BaseEntity {
  id: string;
  label: string;
  translocoId?: string;
  icon?: string;
  /** Absent on a group node, which expands its {@link children} instead of navigating. */
  pageId?: string;
  roles?: string[];
  children?: NavItem[];

  constructor(init: Partial<NavItem> = {}) {
    this.id = init.id ?? '';
    this.label = init.label ?? '';
    this.translocoId = init.translocoId;
    this.icon = init.icon;
    this.pageId = init.pageId;
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
  widgets?: WidgetRef[];

  constructor(init: Partial<RegionDefinition> = {}) {
    this.type = init.type;
    this.navItems = init.navItems;
    this.widgets = init.widgets;
  }
}

export class PageDefinition implements BaseEntity {
  /** Authored, not generated: it is used verbatim as the route path segment. */
  id: string;
  title: string;
  translocoId?: string;
  /** Required by the contract, so a page created here starts with an empty array rather than nothing. */
  widgets: WidgetRef[];

  constructor(init: Partial<PageDefinition> = {}) {
    this.id = init.id ?? '';
    this.title = init.title ?? '';
    this.translocoId = init.translocoId;
    this.widgets = init.widgets ?? [];
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
  pages: PageDefinition[] | undefined;
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
    this.pages = init.pages;
    this.orgKey = init.orgKey;
    this.status = init.status;
    this.version = init.version;
    this.publishedVersion = init.publishedVersion;
    this.createdAt = init.createdAt;
    this.updatedAt = init.updatedAt;
  }
}
