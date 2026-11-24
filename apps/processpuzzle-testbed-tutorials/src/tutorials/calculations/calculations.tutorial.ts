import type { Page } from '@playwright/test';
import type { Tutorial } from '../../recorder/tutorial';

/**
 * The Calculation Designer, shown on the seeded `order-total` calculation: its definition, its source, a validation, its
 * decision tables, an evaluation with the trace that explains each value, and the runs it has produced. The scenario only
 * reads and evaluates; nothing it does changes the calculation, so it needs no reset.
 *
 * Targets are `data-testid`s the generated screens carry, or Material icons, never captions. Order identifiers and the
 * calculation's name are data, the same in every language.
 */
const CALCULATION = 'order-total';

const card = (page: Page) => page.locator('pp-cards-grid mat-card').filter({ has: page.locator('mat-icon', { hasText: 'calculate' }) });
const tab = (name: 'details' | 'source' | 'tables' | 'evaluate' | 'list') => `[data-testid="calculationDefinition-show-${name}"]`;
const calculationRow = (page: Page) => page.locator('tr.mat-mdc-row a', { hasText: new RegExp(`^\\s*${CALCULATION}\\s*$`) }).first();

export const CALCULATIONS: Tutorial = {
  name: 'calculations',
  startPath: '/design',
  ready: (page) => card(page),

  steps: [
    { id: 'intro', action: (stage) => stage.highlight(card(stage.page), 4000) },
    {
      id: 'open',
      action: async (stage) => {
        await stage.click(card(stage.page).locator('mat-card-actions button'));
        await calculationRow(stage.page).waitFor({ state: 'visible' });
      },
    },
    { id: 'list', action: (stage) => stage.highlight(calculationRow(stage.page), 4000) },
    {
      id: 'definition',
      action: async (stage) => {
        await calculationRow(stage.page).click();
        await stage.page.locator('[data-testid="calculationDefinition-name"]').waitFor({ state: 'visible' });
        await stage.highlight('[data-testid="calculationDefinition-name"]', 3000);
        await stage.highlight('[data-testid="calculationDefinition-outputEntity"]', 3000);
      },
    },
    {
      id: 'source',
      action: async (stage) => {
        await stage.click(tab('source'));
        await stage.page.locator('[data-testid="calculation-source-editor"]').waitFor({ state: 'visible' });
        await stage.highlight('[data-testid="calculation-source-editor"]', 5000);
      },
    },
    {
      id: 'validate',
      action: async (stage) => {
        await stage.click('[data-testid="calculation-validate"]');
        await stage.page.waitForTimeout(2000);
      },
    },
    {
      id: 'tables',
      action: async (stage) => {
        await stage.click(tab('tables'));
        await stage.page.waitForTimeout(2500);
        await stage.highlight('main, .mat-drawer-content, body', 3000);
      },
    },
    {
      id: 'choose-order',
      action: async (stage) => {
        await stage.click(tab('evaluate'));
        await stage.page.locator('select[data-testid="calculation-parameter-order"]').waitFor({ state: 'visible' });
        await stage.page.locator('select[data-testid="calculation-parameter-order"]').selectOption({ label: 'ORD-1001' });
        await stage.page.waitForTimeout(800);
      },
    },
    {
      id: 'evaluate',
      action: async (stage) => {
        await stage.click('[data-testid="calculation-evaluate"]');
        // The result is a table; its heading is translated, so the table is waited for rather than the heading.
        await stage.page.locator('table').first().waitFor({ state: 'visible' });
        await stage.page.waitForTimeout(1500);
      },
    },
    { id: 'trace', action: (stage) => stage.highlight(stage.page.locator('table').nth(1), 5000) },
    {
      id: 'runs',
      action: async (stage) => {
        // The tab is named in the language of the recording; its history icon is not.
        await stage.click(stage.page.locator('button, a').filter({ hasText: 'history' }).first());
        await stage.page.locator('[data-testid="calculation-runs-refresh"]').waitFor({ state: 'visible' });
        await stage.highlight('[data-testid="calculation-runs-refresh"]', 3000);
      },
    },
    { id: 'outro', holdMs: 1000 },
  ],
};
