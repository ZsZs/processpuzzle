import type { APIRequestContext, Locator, Page } from '@playwright/test';

/** The languages the testbed offers (`LANGUAGE_CONFIGURATION.AVAILABLE_LANGUAGES`); one video is recorded per language. */
export const TUTORIAL_LANGUAGES = ['en', 'de', 'fr', 'hu', 'es'] as const;
export type TutorialLanguage = (typeof TUTORIAL_LANGUAGES)[number];

/** What an action is aimed at: a CSS selector, or a locator built from `stage.page`. Never a translated text. */
export type Target = string | Locator;

/** What a step's action drives: the page, plus gestures that are visible in the recording. */
export interface Stage {
  page: Page;
  /** The backend the scenario may reach directly, for what the UI should not have to do on screen. */
  backend: TutorialBackend;
  /** The values of the narration file's `values` block — what is typed, in the language being recorded. */
  values: Record<string, string>;
  /** Glide the visible cursor to the element, then click it. */
  click(target: Target): Promise<void>;
  /** Click the element, then type at a human pace. */
  type(target: Target, text: string): Promise<void>;
  /** Glide the cursor over the element. */
  hover(target: Target): Promise<void>;
  /** Draw a temporary outline around the element. */
  highlight(target: Target, ms?: number): Promise<void>;
  pause(ms: number): Promise<void>;
}

export interface TutorialStep {
  /** Key of this step's sentence in every `narration.<lang>.yaml`. */
  id: string;
  /** What happens on screen while the sentence is spoken. Omit to talk over the current screen. */
  action?: (stage: Stage) => Promise<void>;
  /** Time to hold the last frame once narration and action are both over. */
  holdMs?: number;
}

/** What `reset` gets to remove a previous run's data with: a signed-in request context and the backend's root. */
export interface TutorialBackend {
  request: APIRequestContext;
  /** Organization-scoped: `<host>/organizations/<orgKey>`. */
  root: string;
}

export interface Tutorial {
  /** File-name safe; the directory under `src/tutorials` and the key in the published manifest. */
  name: string;
  /** Path the recording starts at, without the language prefix. */
  startPath: string;
  /** Visible once the start screen is ready; the recording is trimmed to begin there. */
  ready: (page: Page) => Locator;
  /**
   * Removes what the scenario creates. Run before and after every recording: the same scenario is recorded once
   * per language, and a leftover of the previous run is a duplicate in the next.
   */
  reset?: (backend: TutorialBackend) => Promise<void>;
  steps: TutorialStep[];
}

/** One spoken sentence placed on the video's time line, relative to the first frame of the published video. */
export interface Cue {
  id: string;
  startMs: number;
  endMs: number;
  text: string;
  /** The synthesized speech; absent when the run had no text-to-speech key and the timing is an estimate. */
  clip?: string;
}
