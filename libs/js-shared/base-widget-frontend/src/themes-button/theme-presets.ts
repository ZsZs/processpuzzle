/**
 * The Material theme presets of `src/theme/pp-material-themes.scss`, in the order they are offered.
 *
 * `processpuzzle` is the brand look and the default. The other eight carry the names of Angular Material's
 * prebuilt themes: the first four use the same Material 3 palettes, the last four are the Material 2 themes
 * re-created from their original seed colours. This list, the stylesheet's `$pp-material-themes` map and the
 * `MaterialTheme` enum of `base-app-api.yaml` have to stay in step — a name missing from the stylesheet
 * silently leaves the subtree unthemed.
 */
export const THEME_PRESETS = [
  'processpuzzle',
  'azure-blue',
  'rose-red',
  'magenta-violet',
  'cyan-orange',
  'indigo-pink',
  'deeppurple-amber',
  'pink-bluegrey',
  'purple-green',
] as const;
export type ThemePreset = (typeof THEME_PRESETS)[number];

/** `auto` follows the `prefers-color-scheme` media query. */
export const THEME_COLOR_SCHEMES = ['light', 'dark', 'auto'] as const;
export type ThemeColorScheme = (typeof THEME_COLOR_SCHEMES)[number];

export const DEFAULT_THEME_PRESET: ThemePreset = 'processpuzzle';
export const DEFAULT_THEME_COLOR_SCHEME: ThemeColorScheme = 'light';

/**
 * Each preset's primary and tertiary colour (tone 40 of its palettes), for drawing a swatch without having
 * to apply the theme first. Read off the palettes in `src/theme`; regenerate alongside them.
 */
export const THEME_PRESET_SWATCHES: Record<ThemePreset, readonly [primary: string, tertiary: string]> = {
  processpuzzle: ['#005eb4', '#006a64'],
  'azure-blue': ['#005cbb', '#343dff'],
  'rose-red': ['#ba005c', '#c00100'],
  'magenta-violet': ['#a900a9', '#7d00fa'],
  'cyan-orange': ['#006a6a', '#964900'],
  'indigo-pink': ['#4355b9', '#bc004b'],
  'deeppurple-amber': ['#6f43c0', '#785900'],
  'pink-bluegrey': ['#bc004b', '#466270'],
  'purple-green': ['#9a25ae', '#006e1c'],
};

export interface ThemeSelection {
  preset: ThemePreset;
  scheme: ThemeColorScheme;
}

export function isThemePreset(value: unknown): value is ThemePreset {
  return (THEME_PRESETS as readonly unknown[]).includes(value);
}

export function isThemeColorScheme(value: unknown): value is ThemeColorScheme {
  return (THEME_COLOR_SCHEMES as readonly unknown[]).includes(value);
}

/** The classes that select a preset and its light/dark half in `pp-material-themes.scss`. */
export function themeClassesFor(selection: ThemeSelection): string[] {
  return [`pp-theme-${selection.preset}`, `pp-scheme-${selection.scheme}`];
}
