import { chromium, type BrowserContextOptions, type Page } from 'playwright';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';

/* ------------------------------------------------------------------ */
/* Scenario types: one file per designer describes script + narration  */
/* ------------------------------------------------------------------ */

export interface TutorialContext {
  page: Page;
  /** Glide the visible cursor to the element, then click it. */
  click(selector: string): Promise<void>;
  /** Click the element, then type with a human-like delay. */
  type(selector: string, text: string): Promise<void>;
  /** Glide the cursor over the element (opens hover menus, draws the eye). */
  hover(selector: string): Promise<void>;
  /** Draw a temporary outline around the element. */
  highlight(selector: string, ms?: number): Promise<void>;
  pause(ms: number): Promise<void>;
}

export interface TutorialStep {
  id: string;
  /** What the voice-over says while this step is on screen. */
  narration: string;
  /** What happens on screen. Omit for a pure "talk over the current screen" step. */
  action?: (ctx: TutorialContext) => Promise<void>;
  /** Extra time to hold the final frame after narration and action are both done. */
  holdMs?: number;
}

export interface Tutorial {
  /** File-name safe, e.g. "entity-designer". */
  name: string;
  title: string;
  /** Path (relative to baseUrl) where the recording starts. */
  startPath: string;
  /**
   * Runs in a separate, NOT recorded browser context (login etc.).
   * Its cookies / localStorage are carried over into the recorded context.
   */
  setup?: (page: Page) => Promise<void>;
  steps: TutorialStep[];
}

export interface RecordOptions {
  baseUrl: string;
  outDir: string;
  /** Set false to run the scenario as a smoke test without writing a video. */
  video?: boolean;
  viewport?: { width: number; height: number };
  /** Speaking speed used to size each step. 2.4 words/s is about 145 wpm. */
  wordsPerSecond?: number;
  headless?: boolean;
}

/* ------------------------------------------------------------------ */
/* Visible cursor + click feedback (Playwright videos have no cursor)  */
/* ------------------------------------------------------------------ */

const CURSOR_SCRIPT = `
(() => {
  if (window.__ppCursor) return;
  window.__ppCursor = true;
  const init = () => {
    const c = document.createElement('div');
    c.style.cssText = 'position:fixed;z-index:2147483647;width:20px;height:20px;border-radius:50%;'
      + 'background:rgba(255,80,0,.55);border:2px solid #fff;box-shadow:0 0 6px rgba(0,0,0,.5);'
      + 'pointer-events:none;transform:translate(-50%,-50%);left:-60px;top:-60px;transition:transform .1s';
    document.body.appendChild(c);
    document.addEventListener('mousemove', e => { c.style.left = e.clientX + 'px'; c.style.top = e.clientY + 'px'; }, true);
    document.addEventListener('mousedown', () => { c.style.transform = 'translate(-50%,-50%) scale(.6)'; }, true);
    document.addEventListener('mouseup', () => { c.style.transform = 'translate(-50%,-50%) scale(1)'; }, true);
  };
  if (document.body) init(); else document.addEventListener('DOMContentLoaded', init);
})();
`;

function makeContext(page: Page): TutorialContext {
  const moveTo = async (selector: string) => {
    const loc = page.locator(selector).first();
    await loc.waitFor({ state: 'visible' });
    await loc.scrollIntoViewIfNeeded();
    const box = await loc.boundingBox();
    if (!box) throw new Error(`No bounding box for selector: ${selector}`);
    const x = box.x + box.width / 2;
    const y = box.y + box.height / 2;
    await page.mouse.move(x, y, { steps: 30 });
    return { x, y };
  };

  return {
    page,
    pause: (ms) => page.waitForTimeout(ms),
    hover: async (selector) => {
      await moveTo(selector);
      await page.waitForTimeout(300);
    },
    click: async (selector) => {
      const { x, y } = await moveTo(selector);
      await page.waitForTimeout(250);
      await page.mouse.click(x, y, { delay: 80 });
    },
    type: async (selector, text) => {
      const { x, y } = await moveTo(selector);
      await page.mouse.click(x, y, { delay: 80 });
      await page.keyboard.type(text, { delay: 70 });
    },
    highlight: async (selector, ms = 2000) => {
      const loc = page.locator(selector).first();
      await loc.waitFor({ state: 'visible' });
      await loc.scrollIntoViewIfNeeded();
      await loc.evaluate((el, duration) => {
        const h = el as HTMLElement;
        const prevOutline = h.style.outline;
        const prevOffset = h.style.outlineOffset;
        h.style.outline = '3px solid #ff5000';
        h.style.outlineOffset = '4px';
        setTimeout(() => {
          h.style.outline = prevOutline;
          h.style.outlineOffset = prevOffset;
        }, duration);
      }, ms);
      await page.waitForTimeout(ms);
    },
  };
}

/* ------------------------------------------------------------------ */
/* Output formats                                                      */
/* ------------------------------------------------------------------ */

interface Cue {
  id: string;
  startMs: number;
  endMs: number;
  text: string;
}

const pad = (n: number, w = 2) => String(n).padStart(w, '0');

function srtTime(ms: number): string {
  const h = Math.floor(ms / 3_600_000);
  const m = Math.floor((ms % 3_600_000) / 60_000);
  const s = Math.floor((ms % 60_000) / 1000);
  return `${pad(h)}:${pad(m)}:${pad(s)},${pad(ms % 1000, 3)}`;
}

function clock(ms: number): string {
  return `${pad(Math.floor(ms / 60_000))}:${pad(Math.floor((ms % 60_000) / 1000))}`;
}

const toSrt = (cues: Cue[]) =>
  cues.map((c, i) => `${i + 1}\n${srtTime(c.startMs)} --> ${srtTime(c.endMs)}\n${c.text}\n`).join('\n');

const toScriptMd = (title: string, cues: Cue[]) =>
  `# ${title}: narration script\n\n` +
  `| Time | Step | Narration |\n|---|---|---|\n` +
  cues.map((c) => `| ${clock(c.startMs)} | ${c.id} | ${c.text} |`).join('\n') +
  '\n';

/* ------------------------------------------------------------------ */
/* Runner                                                              */
/* ------------------------------------------------------------------ */

export async function recordTutorial(tutorial: Tutorial, opts: RecordOptions): Promise<void> {
  const viewport = opts.viewport ?? { width: 1280, height: 720 };
  const wps = opts.wordsPerSecond ?? 2.4;
  const withVideo = opts.video ?? true;
  const outDir = path.join(opts.outDir, tutorial.name);
  await mkdir(outDir, { recursive: true });

  const browser = await chromium.launch({ headless: opts.headless ?? true });
  try {
    // 1) Log in etc. in a context that is not recorded.
    let storageState: BrowserContextOptions['storageState'];
    if (tutorial.setup) {
      const setupContext = await browser.newContext({ viewport, baseURL: opts.baseUrl });
      await tutorial.setup(await setupContext.newPage());
      storageState = await setupContext.storageState();
      await setupContext.close();
    }

    // 2) Recorded context.
    const context = await browser.newContext({
      viewport,
      baseURL: opts.baseUrl,
      storageState,
      ...(withVideo ? { recordVideo: { dir: path.join(outDir, '.tmp'), size: viewport } } : {}),
    });
    await context.addInitScript(CURSOR_SCRIPT);
    const page = await context.newPage();
    const t0 = Date.now(); // video time zero (approximately; see README note on trimming)
    const ctx = makeContext(page);
    const cues: Cue[] = [];

    try {
      await page.goto(tutorial.startPath);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(800); // lead-in so the first frame is not mid-render

      for (const step of tutorial.steps) {
        const words = step.narration.trim().split(/\s+/).length;
        const narrationMs = Math.max(1500, Math.round((words / wps) * 1000));
        const startMs = Date.now() - t0;
        try {
          // Action and narration run in parallel; the step lasts as long as the slower one.
          await Promise.all([step.action?.(ctx), page.waitForTimeout(narrationMs)]);
          await page.waitForTimeout(step.holdMs ?? 400);
        } catch (error) {
          throw new Error(`Step "${step.id}" failed: ${(error as Error).message}`, { cause: error });
        }
        cues.push({ id: step.id, startMs, endMs: Date.now() - t0, text: step.narration });
      }
      await page.waitForTimeout(1000); // tail
    } finally {
      await context.close(); // finalizes the video file
    }

    if (withVideo) {
      const video = page.video();
      if (video) {
        await video.saveAs(path.join(outDir, `${tutorial.name}.webm`));
        await video.delete();
      }
    }
    await writeFile(path.join(outDir, `${tutorial.name}.srt`), toSrt(cues), 'utf8');
    await writeFile(path.join(outDir, `${tutorial.name}.script.md`), toScriptMd(tutorial.title, cues), 'utf8');
    await writeFile(
      path.join(outDir, `${tutorial.name}.narration.txt`),
      cues.map((c) => c.text).join('\n\n') + '\n',
      'utf8',
    );
    console.log(`[tutorial] ${tutorial.name}: ${cues.length} steps -> ${outDir}`);
  } finally {
    await browser.close();
  }
}
