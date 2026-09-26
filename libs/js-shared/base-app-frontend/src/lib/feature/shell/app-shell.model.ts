import { AppDefinition, ColorScheme, LayoutPreset, MaterialTheme, SidenavMode } from '../../domain/app-definition';

/**
 * The layout decisions {@link AppShellComponent} renders from, resolved out of an `AppDefinition`'s
 * `layout` fields with every default already applied — so the template reads values rather than
 * spelling out fallbacks, and every default is asserted in one spec instead of being implied by a
 * `??` in markup.
 *
 * A plain function rather than a service for the same reason `buildAppRoutes` is one: nothing here
 * needs injection, and a pure derivation is testable without a `TestBed`.
 */
export interface ResolvedLayout {
  preset: LayoutPreset;
  /**
   * Always resolved, even for `top-nav` where it is meaningless — {@link hasSidenav} is what says
   * whether a sidenav is rendered at all. Leaving it undefined there would only push a `?? 'side'`
   * into the template that no definition can ever reach, since `hasSidenav` already implies a mode.
   */
  sidenavMode: SidenavMode;
  /** `end` for `sidenav-right`, `start` otherwise — the values `matSidenav`'s `position` takes. */
  sidenavPosition: 'start' | 'end';
  /** Whether the shell renders a sidenav at all. False for `top-nav`. */
  hasSidenav: boolean;
  sidenavOpened: boolean;
  sidenavCollapsible: boolean;
  /** Undefined means unconstrained, which is what an absent `contentMaxWidth` asks for. */
  contentMaxWidth?: string;
}

/**
 * `sidenav-left` because a definition that declares no preset is the common case — a freshly created
 * app — and the framework's own screens are left-sidenav. The same value the contract documents as
 * its default.
 */
const DEFAULT_PRESET: LayoutPreset = 'sidenav-left';

/** Matches the contract's default and Material's own, so an unset value behaves as `mat-sidenav` does. */
const DEFAULT_SIDENAV_MODE: SidenavMode = 'side';

/**
 * Resolves the layout of one definition.
 *
 * Each field is read from the flattened property first and from `layout` second. For an entity that
 * came through {@link AppDefinitionMapper} the two always agree — `fromDto` copies `layout.preset`
 * onto `preset`, and `toDto` merges the flattened value back — so the fallback is there for the
 * definitions nothing mapped: `new AppDefinition({ layout: { preset: 'top-nav' } })` is a legal
 * construction that leaves `preset` undefined, and a shell that ignored `layout` would silently
 * render the default instead.
 *
 * `sidenavCollapsible` and `sidenavOpenByDefault` need no such fallback: they are non-optional on the
 * entity and the constructor defaults them, so there is always a value to read.
 */
export function layoutOf(definition: AppDefinition | undefined): ResolvedLayout {
  const preset = definition?.preset ?? definition?.layout?.preset ?? DEFAULT_PRESET;

  return {
    preset,
    sidenavMode: definition?.sidenavMode ?? definition?.layout?.sidenavMode ?? DEFAULT_SIDENAV_MODE,
    sidenavPosition: preset === 'sidenav-right' ? 'end' : 'start',
    hasSidenav: preset !== 'top-nav',
    sidenavOpened: definition?.sidenavOpenByDefault ?? true,
    sidenavCollapsible: definition?.sidenavCollapsible ?? true,
    contentMaxWidth: definition?.contentMaxWidth ?? definition?.layout?.contentMaxWidth,
  };
}

/**
 * The `--pp-*` overrides of one definition, as a style object to bind on the shell's host element.
 *
 * Custom properties cascade, so setting them on one element re-tints every framework surface below it
 * — which is what makes a themed preview possible at all without an iframe. `materialTheme` and
 * `colorScheme` are not handled here but by {@link themeClassOf}, as classes on the same element.
 *
 * Keys are passed through untouched, including their leading `--`: Angular's `[style]` binding treats
 * a custom property as a custom property, and the contract calls these overrides of the tokens in
 * `pp-colors.css`, so anything else would be this function inventing a naming rule.
 */
export function themeVarsOf(definition: AppDefinition | undefined): Record<string, string> {
  // The nested object first, so an override the form actually edited wins over the one the server sent.
  return { ...definition?.theme?.tokenOverrides, ...definition?.tokenOverrides };
}

/**
 * The route paths this definition accounts for: its own routes' paths, plus the base path of every module
 * it mounts.
 *
 * A module's *own* routes are deliberately absent. They live in the module's definition, which the router
 * fetches only when something navigates into the mount, so they cannot be known here — which is why the
 * consumer of this list treats a path *below* a known one as accounted for. See `toNavRows`.
 */
export function knownRoutePathsOf(definition: AppDefinition | undefined): string[] {
  const declared = (definition?.routes ?? []).map((route) => route.path);
  const mounted = (definition?.modules ?? []).map((mount) => mount.basePath);
  return [...declared, ...mounted].filter((path): path is string => !!path);
}

/** The theme and scheme a definition that names none is rendered in, matching the contract's own defaults. */
const DEFAULT_MATERIAL_THEME: MaterialTheme = 'processpuzzle';
const DEFAULT_COLOR_SCHEME: ColorScheme = 'light';

/**
 * Which of the scoped Material themes in `src/theme/pp-material-themes.scss` the shell should wear, as the
 * class names that select it — `pp-theme-<materialTheme> pp-scheme-<colorScheme>`.
 *
 * A definition naming no theme wears `processpuzzle`, the contract's default, rather than inheriting the
 * host's: the shell's surfaces are painted by `--pp-*` tokens that each preset declares, and a shell that
 * inherited them would look like whatever application happened to host it. The server fills the same
 * default in on save, so this only decides how an unsaved or hand-built definition renders.
 */
export function themeClassOf(definition: AppDefinition | undefined): string {
  const materialTheme = definition?.materialTheme ?? definition?.theme?.materialTheme ?? DEFAULT_MATERIAL_THEME;

  const colorScheme = definition?.colorScheme ?? definition?.theme?.colorScheme ?? DEFAULT_COLOR_SCHEME;
  return `pp-theme-${materialTheme} pp-scheme-${colorScheme}`;
}
