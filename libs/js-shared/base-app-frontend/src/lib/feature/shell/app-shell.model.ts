import { AppDefinition, LayoutPreset, SidenavMode } from '../../domain/app-definition';

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
 * `colorScheme` are deliberately **not** handled: those want `:root` and a Material theme class, and
 * neither scopes to a subtree. They belong to the standalone runtime host, where the shell owns the
 * document.
 *
 * Keys are passed through untouched, including their leading `--`: Angular's `[style]` binding treats
 * a custom property as a custom property, and the contract calls these overrides of the tokens in
 * `pp-colors.css`, so anything else would be this function inventing a naming rule.
 */
export function themeVarsOf(definition: AppDefinition | undefined): Record<string, string> {
  // The nested object first, so an override the form actually edited wins over the one the server sent.
  return { ...definition?.theme?.tokenOverrides, ...definition?.tokenOverrides };
}
