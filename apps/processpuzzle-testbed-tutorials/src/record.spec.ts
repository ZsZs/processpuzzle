import { expect, test } from '@playwright/test';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { recordTutorial } from './recorder/recorder';
import { loadNarration } from './recorder/narration';
import { TUTORIAL_LANGUAGES, type TutorialLanguage } from './recorder/tutorial';
import { serviceRoots } from './support/backend';
import { OUT_DIRECTORY, SPEECH_CACHE_DIRECTORY, STORAGE_STATE } from './support/paths';
import { TUTORIALS } from './tutorials';

/**
 * One recording per tutorial and language. Narrow a run with Playwright's own `--grep`, e.g.
 * `--grep "entity-designer \[hu\]"`, or with `TUTORIAL_LANGS=en,hu`; `TUTORIAL_VIDEO=false` (the `smoke` target)
 * drives the scenarios without recording them.
 */
const requested = process.env['TUTORIAL_LANGS']?.split(',').map((language) => language.trim());
const languages = TUTORIAL_LANGUAGES.filter((language) => !requested || requested.includes(language));
const video = process.env['TUTORIAL_VIDEO'] !== 'false';

for (const tutorial of TUTORIALS) {
  // Every language's file is held against the scenario even when only some are recorded: a sentence missing from the
  // French narration should not wait for the day French is recorded.
  test(`${tutorial.name} narration is complete`, () => {
    for (const language of TUTORIAL_LANGUAGES) expect(() => loadNarration(tutorial, language)).not.toThrow();
  });

  for (const language of languages as TutorialLanguage[]) {
    test(`${tutorial.name} [${language}]`, async ({ browser, request, baseURL }) => {
      const roots = await serviceRoots(request, baseURL as string);
      await recordTutorial(browser, tutorial, language, {
        baseUrl: baseURL as string,
        storageState: STORAGE_STATE,
        backend: { request, root: roots.backend },
        outDirectory: OUT_DIRECTORY,
        speechCacheDirectory: SPEECH_CACHE_DIRECTORY,
        video,
      });
      expect(existsSync(path.join(OUT_DIRECTORY, tutorial.name, language, 'tutorial.json'))).toBe(true);
    });
  }
}
