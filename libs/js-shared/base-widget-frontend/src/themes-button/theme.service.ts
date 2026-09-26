import { DOCUMENT, effect, inject, Injectable, signal, computed } from '@angular/core';
import {
  DEFAULT_THEME_COLOR_SCHEME,
  DEFAULT_THEME_PRESET,
  isThemeColorScheme,
  isThemePreset,
  ThemeColorScheme,
  ThemePreset,
  themeClassesFor,
  ThemeSelection,
} from './theme-presets';

/** Where the root instance keeps the user's choice for the host application itself. */
export const DOCUMENT_THEME_STORAGE_KEY = 'pp-theme';

/**
 * Which theme preset and colour scheme a subtree wears: the user's choice where they made one, otherwise the
 * defaults its owner declared, otherwise the ProcessPuzzle look.
 *
 * There are two kinds of instance, and a `ThemesButtonComponent` talks to whichever is nearest:
 * - **The root instance** themes the host application. It persists under {@link DOCUMENT_THEME_STORAGE_KEY}
 *   and puts the theme classes on `<html>` — but only once the user has chosen something, so that a host
 *   application keeps its own Material theme until then.
 * - **A scoped instance**, listed in a component's `providers`, themes that component's subtree. It applies
 *   nothing itself; the owner binds {@link themeClass} and sets {@link setDefaults} and {@link persistUnder}.
 *   base-app's `AppShellComponent` is one: the defaults come from the app definition, the choice is stored
 *   per application.
 *
 * Either way the classes only take effect where `pp-material-themes.scss` is registered as a global style.
 */
@Injectable({ providedIn: 'root', useFactory: createDocumentThemeService })
export class ThemeService {
  private readonly _defaults = signal<Partial<ThemeSelection>>({});
  private readonly _choice = signal<Partial<ThemeSelection>>({});
  private storageKey: string | null = null;

  /** What the user picked, field by field; empty until they pick something. */
  readonly choice = this._choice.asReadonly();
  readonly hasChoice = computed(() => this._choice().preset !== undefined || this._choice().scheme !== undefined);
  readonly selection = computed<ThemeSelection>(() => ({
    preset: this._choice().preset ?? this._defaults().preset ?? DEFAULT_THEME_PRESET,
    scheme: this._choice().scheme ?? this._defaults().scheme ?? DEFAULT_THEME_COLOR_SCHEME,
  }));
  readonly themeClass = computed(() => themeClassesFor(this.selection()).join(' '));

  setDefaults(defaults: Partial<ThemeSelection>): void {
    this._defaults.set(defaults);
  }

  /**
   * Starts reading and writing the user's choice under `key`, replacing whatever choice is held now with the
   * one stored there. `null` keeps choices in memory only — the designer's preview does that, so that a theme
   * tried out there never outlives the preview or shadows the definition being edited.
   */
  persistUnder(key: string | null): void {
    this.storageKey = key;
    this._choice.set(key ? readChoice(key) : {});
  }

  selectPreset(preset: ThemePreset): void {
    this.updateChoice({ ...this._choice(), preset });
  }

  selectScheme(scheme: ThemeColorScheme): void {
    this.updateChoice({ ...this._choice(), scheme });
  }

  /** Forgets the user's choice, returning to the declared defaults. */
  reset(): void {
    this.updateChoice({});
  }

  private updateChoice(choice: Partial<ThemeSelection>): void {
    this._choice.set(choice);
    if (this.storageKey) writeChoice(this.storageKey, choice);
  }
}

function createDocumentThemeService(): ThemeService {
  const service = new ThemeService();
  service.persistUnder(DOCUMENT_THEME_STORAGE_KEY);

  const root = inject(DOCUMENT).documentElement;
  let applied: string[] = [];
  effect(() => {
    const classes = service.hasChoice() ? themeClassesFor(service.selection()) : [];
    root.classList.remove(...applied);
    root.classList.add(...classes);
    applied = classes;
  });
  return service;
}

/** Reads a stored choice, dropping anything that is not a current preset or scheme rather than applying it. */
function readChoice(key: string): Partial<ThemeSelection> {
  try {
    const stored = JSON.parse(globalThis.localStorage?.getItem(key) ?? '{}');
    return {
      ...(isThemePreset(stored?.preset) ? { preset: stored.preset } : {}),
      ...(isThemeColorScheme(stored?.scheme) ? { scheme: stored.scheme } : {}),
    };
  } catch {
    return {};
  }
}

function writeChoice(key: string, choice: Partial<ThemeSelection>): void {
  try {
    if (choice.preset === undefined && choice.scheme === undefined) globalThis.localStorage?.removeItem(key);
    else globalThis.localStorage?.setItem(key, JSON.stringify(choice));
  } catch {
    // Storage unavailable (private mode, quota): the choice still holds for this session.
  }
}
