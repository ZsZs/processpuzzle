import type { Browser, BrowserContextOptions } from '@playwright/test';
import { mkdirSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { assembleVideo, toTranscript, toVtt } from './media';
import { loadNarration } from './narration';
import { speak, type SpeechClip } from './speech';
import { createStage, CURSOR_SCRIPT } from './stage';
import type { Cue, Tutorial, TutorialBackend, TutorialLanguage } from './tutorial';

export interface RecordOptions {
  baseUrl: string;
  /** The signed-in session the recording starts from; signing in is not part of any tutorial. */
  storageState: BrowserContextOptions['storageState'];
  backend: TutorialBackend;
  /** Receives `<tutorial>/<language>/`. */
  outDirectory: string;
  speechCacheDirectory: string;
  /** `false` runs the scenario without recording — the smoke run that tells a UI change from a broken tutorial. */
  video: boolean;
}

/** Wide enough for a generated form beside the designer's sidenav; at 1280 the form's last column is cut off. */
const VIEWPORT = { width: 1600, height: 900 };
/** Silence between two steps, so that sentences do not run into each other. */
const STEP_GAP_MS = 450;
/** What is kept of the start screen before the first sentence, and of the last one after the last. */
const LEAD_IN_MS = 600;
const TAIL_MS = 1200;
const ACTION_TIMEOUT_MS = 20_000;
/** What a step may take beyond its sentence before it is declared stuck. */
const STEP_LIMIT_MS = 60_000;

async function withLimit<T>(work: Promise<T>, limitMs: number): Promise<T> {
  let timer: NodeJS.Timeout | undefined;
  const expired = new Promise<never>((_, reject) => {
    timer = setTimeout(() => reject(new Error(`took longer than ${Math.round(limitMs / 1000)}s; the page is probably frozen`)), limitMs);
  });
  try {
    return await Promise.race([work, expired]);
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Records one tutorial in one language: speech first, then the browser session sized by it, then the cut.
 *
 * Each step lasts as long as the longer of its action and its sentence. The sentence's length is the synthesized
 * clip's, so the published video and its voice-over cannot drift apart, in any language.
 */
export async function recordTutorial(browser: Browser, tutorial: Tutorial, language: TutorialLanguage, options: RecordOptions): Promise<void> {
  const narration = loadNarration(tutorial, language);
  const directory = path.join(options.outDirectory, tutorial.name, language);
  rmSync(directory, { recursive: true, force: true });
  mkdirSync(directory, { recursive: true });

  const clips = new Map<string, SpeechClip>();
  for (const step of tutorial.steps) {
    clips.set(step.id, await speak(narration.steps[step.id], language, options.speechCacheDirectory));
  }

  await tutorial.reset?.(options.backend);
  const context = await browser.newContext({
    viewport: VIEWPORT,
    baseURL: options.baseUrl,
    storageState: options.storageState,
    locale: language,
    ...(options.video ? { recordVideo: { dir: path.join(directory, 'raw'), size: VIEWPORT } } : {}),
  });
  // A target the screen no longer has must fail its step, by name, rather than wait out the whole test.
  context.setDefaultTimeout(ACTION_TIMEOUT_MS);
  await context.addInitScript(CURSOR_SCRIPT);
  const page = await context.newPage();
  const recordingStarted = Date.now(); // the raw video's time zero, to within a frame or two
  // What the application says while it is being recorded: the first place to look when a step fails.
  page.on('pageerror', (error) => console.warn(`[tutorial] ${tutorial.name}/${language} page error: ${error.message.slice(0, 300)}`));
  page.on('crash', () => console.error(`[tutorial] ${tutorial.name}/${language} the page crashed at ${page.url()}`));
  const stage = createStage(page, narration.values, options.backend);
  const cues: Cue[] = [];
  let trimMs: number | undefined; // set once the start screen is ready

  try {
    // The language is the first path segment: `provideLocaleRouting` reads it and prefixes every later navigation.
    await page.goto(`/${language}${tutorial.startPath}`);
    await tutorial.ready(page).first().waitFor({ state: 'visible', timeout: 60_000 });
    await page.waitForTimeout(LEAD_IN_MS);
    trimMs = Math.max(0, Date.now() - recordingStarted - LEAD_IN_MS);

    for (const step of tutorial.steps) {
      const clip = clips.get(step.id) as SpeechClip;
      const startMs = Date.now() - recordingStarted - (trimMs ?? 0);
      try {
        // A frozen page does not time out the calls made to it, so the step is given a limit of its own.
        await withLimit(Promise.all([step.action?.(stage), page.waitForTimeout(clip.durationMs)]), clip.durationMs + STEP_LIMIT_MS);
      } catch (error) {
        await page.screenshot({ path: path.join(directory, `failed-${step.id}.png`) }).catch(() => undefined);
        throw new Error(`[${tutorial.name}/${language}] step "${step.id}" failed: ${(error as Error).message}`, { cause: error });
      }
      await page.waitForTimeout(step.holdMs ?? STEP_GAP_MS);
      console.log(`[tutorial] ${tutorial.name}/${language} ${step.id}: ${((Date.now() - recordingStarted - (trimMs ?? 0) - startMs) / 1000).toFixed(1)}s (speech ${(clip.durationMs / 1000).toFixed(1)}s)`);
      cues.push({ id: step.id, startMs, endMs: Date.now() - recordingStarted - (trimMs ?? 0), text: narration.steps[step.id], clip: clip.file });
    }
    await page.waitForTimeout(TAIL_MS);
  } finally {
    await context.close(); // finalizes the video file
    await tutorial.reset?.(options.backend);
  }

  const spokenMs = (cue: Cue) => Math.min((clips.get(cue.id) as SpeechClip).durationMs, cue.endMs - cue.startMs);
  writeFileSync(path.join(directory, `${tutorial.name}.vtt`), toVtt(cues, spokenMs), 'utf8');
  writeFileSync(path.join(directory, `${tutorial.name}.md`), toTranscript(narration.title, cues), 'utf8');
  const durationMs = (cues.at(-1)?.endMs ?? 0) + TAIL_MS;
  writeFileSync(
    path.join(directory, 'tutorial.json'),
    JSON.stringify({ name: tutorial.name, language, title: narration.title, durationSeconds: Math.round(durationMs / 1000), narrated: cues.some((cue) => cue.clip) }, null, 2),
    'utf8',
  );

  if (options.video) {
    await page.video()?.saveAs(path.join(directory, 'raw.webm'));
    assembleVideo({ directory, rawVideo: 'raw.webm', trimMs: trimMs ?? 0, cues, output: `${tutorial.name}.mp4` });
    rmSync(path.join(directory, 'raw'), { recursive: true, force: true });
  }
  console.log(`[tutorial] ${tutorial.name}/${language}: ${cues.length} steps, ${Math.round(durationMs / 1000)}s -> ${directory}`);
}
