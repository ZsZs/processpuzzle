import { BaseEntity } from '@processpuzzle/base-entity';

/**
 * Frontend model of the `AppDefinition` schema of `base-app-api.yaml`. `theme` and `layout` are
 * flattened onto the entity by {@link AppDefinitionMapper} so that the generated form can offer a
 * typed control per field; the original objects are kept alongside, because a full replacement PUT
 * would otherwise drop the parts no control writes. `regions` and `pages` stay nested and are edited
 * through the `RELATED_ENTITIES` controls of the `App Region` / `App Page` descriptors.
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

export interface WidgetRef {
  id: string;
  type: string;
  props?: Record<string, unknown>;
  children?: WidgetRef[];
}

export interface NavItem {
  id: string;
  label: string;
  translocoId?: string;
  icon?: string;
  pageId?: string;
  roles?: string[];
  children?: NavItem[];
}

export interface RegionDefinition {
  type: RegionType;
  navItems?: NavItem[];
  widgets?: WidgetRef[];
}

export interface PageDefinition {
  id: string;
  title: string;
  translocoId?: string;
  widgets: WidgetRef[];
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
