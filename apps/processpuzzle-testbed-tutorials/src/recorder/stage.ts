import type { Locator, Page } from '@playwright/test';
import type { Stage, Target, TutorialBackend } from './tutorial';

/**
 * A cursor the recording can show: Playwright's video has none. Installed as an init script, so it survives every
 * navigation; it follows the real mouse events the stage's gestures produce.
 */
export const CURSOR_SCRIPT = `
(() => {
  if (window.__ppTutorialCursor) return;
  window.__ppTutorialCursor = true;
  const init = () => {
    const cursor = document.createElement('div');
    cursor.style.cssText = 'position:fixed;z-index:2147483647;width:20px;height:20px;border-radius:50%;'
      + 'background:rgba(255,80,0,.55);border:2px solid #fff;box-shadow:0 0 6px rgba(0,0,0,.5);'
      + 'pointer-events:none;transform:translate(-50%,-50%);left:-60px;top:-60px;transition:transform .1s';
    // A tooltip left by the previous hover can sit over the next field and take its click. It stays readable, but the
    // pointer goes through it.
    const style = document.createElement('style');
    style.textContent = 'mat-tooltip-component, .mat-mdc-tooltip, .mdc-tooltip { pointer-events: none !important; }';
    document.head.appendChild(style);
    document.body.appendChild(cursor);
    document.addEventListener('mousemove', (e) => { cursor.style.left = e.clientX + 'px'; cursor.style.top = e.clientY + 'px'; }, true);
    document.addEventListener('mousedown', () => { cursor.style.transform = 'translate(-50%,-50%) scale(.6)'; }, true);
    document.addEventListener('mouseup', () => { cursor.style.transform = 'translate(-50%,-50%) scale(1)'; }, true);
  };
  if (document.body) init(); else document.addEventListener('DOMContentLoaded', init);
})();
`;

export function createStage(page: Page, values: Record<string, string>, backend: TutorialBackend): Stage {
  const locate = (target: Target): Locator => (typeof target === 'string' ? page.locator(target) : target).first();

  const moveTo = async (target: Target) => {
    const locator = locate(target);
    await locator.waitFor({ state: 'visible' });
    await locator.scrollIntoViewIfNeeded();
    const box = await locator.boundingBox();
    if (!box) throw new Error(`no bounding box for ${String(target)}`);
    const point = { x: box.x + box.width / 2, y: box.y + box.height / 2 };
    await page.mouse.move(point.x, point.y, { steps: 30 });
    return point;
  };

  return {
    page,
    values,
    backend,
    pause: (ms) => page.waitForTimeout(ms),
    hover: async (target) => {
      await moveTo(target);
      await page.waitForTimeout(300);
    },
    click: async (target) => {
      const { x, y } = await moveTo(target);
      await page.waitForTimeout(250);
      await page.mouse.click(x, y, { delay: 80 });
    },
    type: async (target, text) => {
      // A form that has only just opened may still be filled in from the store, which wipes what was typed meanwhile
      // — Save then stays disabled and the step waits for nothing. So the value is checked, and typed again if lost.
      for (let attempt = 1; ; attempt++) {
        const { x, y } = await moveTo(target);
        await page.mouse.click(x, y, { delay: 80 });
        await page.keyboard.press('ControlOrMeta+A');
        await page.keyboard.type(text, { delay: 60 });
        await page.waitForTimeout(500);
        if ((await locate(target).inputValue()) === text) return;
        if (attempt === 3) throw new Error(`the text typed into ${String(target)} did not stay`);
      }
    },
    highlight: async (target, ms = 2000) => {
      const locator = locate(target);
      await locator.waitFor({ state: 'visible' });
      await locator.scrollIntoViewIfNeeded();
      await locator.evaluate((element, duration) => {
        const host = element as HTMLElement;
        const previous = { outline: host.style.outline, offset: host.style.outlineOffset };
        host.style.outline = '3px solid #ff5000';
        host.style.outlineOffset = '4px';
        setTimeout(() => {
          host.style.outline = previous.outline;
          host.style.outlineOffset = previous.offset;
        }, duration);
      }, ms);
      await page.waitForTimeout(ms);
    },
  };
}
