import type { Page } from '@playwright/test';
import type { Stage, Tutorial } from '../../recorder/tutorial';

/**
 * The Rule Designer: write a rule that checks an entity's data, and see it in the list. The rule's expression is code,
 * the same in every language; its name and message are what the scenario types, from the narration's values.
 *
 * Targets are `data-testid`s the generated rule form carries (`baseRule-<attribute>`) or Material icons, never a
 * caption — the same scenario is recorded in five languages.
 */
const CONTEXT = 'Dynamic Entity';
const EXPRESSION = 'entity.numberAttr === null || entity.numberAttr >= 0';

const field = (attribute: string) => `[data-testid="baseRule-${attribute}"]`;
const rulesCard = (page: Page) => page.locator('pp-cards-grid mat-card').filter({ has: page.locator('mat-icon', { hasText: 'gavel' }) });
/** A list or toolbar control, found by its icon, which does not change with the language. */
const withIcon = (page: Page, icon: string) => page.locator('button, a').filter({ has: page.locator('mat-icon', { hasText: new RegExp(`^${icon}$`) }) });

/** A `mat-select` of the generated form, choosing an option by its visible text. */
async function choose(stage: Stage, selector: string, option: string): Promise<void> {
  await stage.click(`${selector} mat-select`);
  await stage.click(stage.page.getByRole('option', { name: option, exact: true }));
}

export const RULES: Tutorial = {
  name: 'rules',
  startPath: '/design',
  ready: (page) => rulesCard(page),

  reset: async ({ request, root }) => {
    // Rules have no key of their own to address, so the one this scenario writes is found by its expression.
    const response = await request.get(`${root}/rules?size=500`);
    const { content = [] } = (await response.json()) as { content?: { id: string; expression: string }[] };
    for (const rule of content.filter((candidate) => candidate.expression === EXPRESSION)) {
      const removed = await request.delete(`${root}/rules/${encodeURIComponent(rule.id)}`);
      if (![200, 204, 404].includes(removed.status())) {
        throw new Error(`removing the tutorial rule ${rule.id} answered ${removed.status()}: ${await removed.text()}`);
      }
    }
  },

  steps: [
    { id: 'intro', action: (stage) => stage.highlight(rulesCard(stage.page), 3000) },
    {
      id: 'open',
      action: async (stage) => {
        await stage.click(rulesCard(stage.page).locator('mat-card-actions button'));
        await stage.page.locator('base-list tr.mat-mdc-row, table').first().waitFor({ state: 'visible' });
      },
    },
    { id: 'list', action: (stage) => stage.highlight('base-list table', 3500) },
    {
      id: 'new',
      action: async (stage) => {
        await stage.click(withIcon(stage.page, 'add').first());
        await stage.page.locator(field('name')).waitFor({ state: 'visible' });
      },
    },
    {
      id: 'identify',
      action: async (stage) => {
        await stage.type(`${field('name')} input`, stage.values['ruleName']);
        await choose(stage, field('context'), CONTEXT);
        await choose(stage, field('severity'), 'ERROR');
      },
    },
    {
      id: 'write-expression',
      action: async (stage) => {
        await stage.type(`${field('expression')} textarea`, EXPRESSION);
        await stage.type(`${field('message')} input`, stage.values['ruleMessage']);
      },
    },
    {
      id: 'save',
      action: async (stage) => {
        await stage.click(field('save'));
        await stage.page.locator('tr.mat-mdc-row', { hasText: stage.values['ruleName'] }).first().waitFor({ state: 'visible' });
      },
    },
    { id: 'show-result', action: (stage) => stage.highlight(stage.page.locator('tr.mat-mdc-row', { hasText: stage.values['ruleName'] }).first(), 4000) },
    { id: 'outro', holdMs: 1000 },
  ],
};
