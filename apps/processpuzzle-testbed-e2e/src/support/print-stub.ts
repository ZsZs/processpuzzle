import { Page } from '@playwright/test';

type Counter = { __printCalls?: number };

/**
 * Replaces `window.print` with a recorder, before any page script runs, in every frame.
 *
 * A report's Print button prints a copy of the report from a hidden iframe (`printElement`), so it is the
 * *iframe's* `print` that has to be caught; the calls are counted on the top window. The browser's print dialog
 * cannot be driven by Playwright and would block the page. The PDF a signature produces is a different matter —
 * that is real jsPDF output and is deliberately not stubbed. Read the count with {@link printCalls}.
 */
export async function stubPrint(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const top = window.top as unknown as Counter;
    top.__printCalls ??= 0;
    window.print = () => {
      top.__printCalls = (top.__printCalls ?? 0) + 1;
    };
  });
}

export async function printCalls(page: Page): Promise<number> {
  return page.evaluate(() => (window as unknown as Counter).__printCalls ?? 0);
}
