import type { Page } from '@playwright/test';
import type { Stage, Tutorial } from '../../recorder/tutorial';

/**
 * The Workflow Designer, shown on a seeded workflow: its definition, its modeler, and the parts a workflow is made of —
 * roles, tasks, artifacts, tools, events — and the instances that have run. The scenario only reads and looks; nothing it
 * does changes the workflow, so it needs no reset.
 *
 * The workflow is found by its code, which is the same in every language. The sections are chosen by their Material icon, which does not
 * change with the language; the captions do.
 */
const WORKFLOW = /^\s*order-fulfillment-workflow\s*$/;

const card = (page: Page) => page.locator('pp-cards-grid mat-card').filter({ has: page.locator('mat-icon', { hasText: 'schema' }) });
const workflowRow = (page: Page) => page.locator('tr.mat-mdc-row a', { hasText: WORKFLOW }).first();
const section = (page: Page, icon: string) => page.locator('a, button').filter({ hasText: new RegExp(`^\\s*${icon}\\b`) }).first();

async function openSection(stage: Stage, icon: string): Promise<void> {
  await stage.click(section(stage.page, icon));
  await stage.page.waitForTimeout(2500);
  await stage.highlight('main, .mat-drawer-content, body', 3500);
}

export const WORKFLOWS: Tutorial = {
  name: 'workflows',
  startPath: '/design',
  ready: (page) => card(page),

  steps: [
    { id: 'intro', action: (stage) => stage.highlight(card(stage.page), 4000) },
    {
      id: 'open',
      action: async (stage) => {
        await stage.click(card(stage.page).locator('mat-card-actions button'));
        await workflowRow(stage.page).waitFor({ state: 'visible' });
      },
    },
    { id: 'list', action: (stage) => stage.highlight('base-list table', 4000) },
    {
      id: 'definition',
      action: async (stage) => {
        await workflowRow(stage.page).click();
        await stage.page.locator('[data-testid="workflow-name"]').waitFor({ state: 'visible' });
        await stage.highlight('[data-testid="workflow-name"]', 3000);
        await stage.highlight('[data-testid="workflow-description"]', 3500);
      },
    },
    {
      id: 'modeler',
      action: async (stage) => {
        await stage.click('[data-testid="workflow-show-modeler"]');
        await stage.page.waitForTimeout(3000);
        await stage.highlight('main, .mat-drawer-content, body', 5000);
      },
    },
    { id: 'roles', action: (stage) => openSection(stage, 'badge') },
    { id: 'tasks', action: (stage) => openSection(stage, 'assignment') },
    { id: 'artifacts', action: (stage) => openSection(stage, 'inventory_2') },
    { id: 'tools', action: (stage) => openSection(stage, 'build') },
    { id: 'events', action: (stage) => openSection(stage, 'bolt') },
    { id: 'instances', action: (stage) => openSection(stage, 'play_circle') },
    { id: 'outro', holdMs: 1000 },
  ],
};
