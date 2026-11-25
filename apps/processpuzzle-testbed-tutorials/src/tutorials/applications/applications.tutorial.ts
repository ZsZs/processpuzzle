import type { Page } from '@playwright/test';
import type { Tutorial } from '../../recorder/tutorial';

/**
 * The Application Designer, shown on the seeded order-desk application: its definition and appearance, its structure
 * (regions, routes, modules, contexts), its preview as users see it, and the modules and widgets it is built from. The
 * scenario only reads and previews; nothing it does changes the application, so it needs no reset. The application's
 * code and its name are data, the same in every language.
 */
const APPLICATION = /^\s*order-desk\s*$/;

const card = (page: Page) => page.locator('pp-cards-grid mat-card').filter({ has: page.locator('mat-icon', { hasText: 'web' }) });
const applicationRow = (page: Page) => page.locator('tr.mat-mdc-row a', { hasText: APPLICATION }).first();
const section = (page: Page, icon: string) => page.locator('a, button').filter({ hasText: new RegExp(`^\\s*${icon}\\b`) }).first();

export const APPLICATIONS: Tutorial = {
  name: 'applications',
  startPath: '/design',
  ready: (page) => card(page),

  steps: [
    { id: 'intro', action: (stage) => stage.highlight(card(stage.page), 4000) },
    {
      id: 'open',
      action: async (stage) => {
        await stage.click(card(stage.page).locator('mat-card-actions button'));
        await applicationRow(stage.page).waitFor({ state: 'visible' });
      },
    },
    { id: 'list', action: (stage) => stage.highlight('base-list table', 4000) },
    {
      id: 'details',
      action: async (stage) => {
        await applicationRow(stage.page).click();
        await stage.page.locator('[data-testid="appDefinition-name"]').waitFor({ state: 'visible' });
        await stage.highlight('[data-testid="appDefinition-name"]', 3000);
        await stage.highlight('[data-testid="appDefinition-description"]', 3500);
      },
    },
    {
      id: 'appearance',
      action: async (stage) => {
        await stage.highlight('[data-testid="appDefinition-materialTheme"]', 3000);
        await stage.highlight('[data-testid="appDefinition-colorScheme"]', 3000);
        await stage.highlight('[data-testid="appDefinition-preset"]', 3500);
        await stage.highlight('[data-testid="appDefinition-sidenavMode"]', 3500);
      },
    },
    {
      id: 'structure',
      action: async (stage) => {
        await stage.highlight('[data-testid="appDefinition-regions"]', 3500);
        await stage.highlight('[data-testid="appDefinition-routes"]', 3500);
        await stage.highlight('[data-testid="appDefinition-modules"]', 3000);
        await stage.highlight('[data-testid="appDefinition-contexts"]', 3000);
      },
    },
    {
      id: 'preview',
      action: async (stage) => {
        await stage.click('[data-testid="appDefinition-show-preview"]');
        await stage.page.waitForTimeout(3500);
        await stage.highlight('main, .mat-drawer-content, body', 5000);
      },
    },
    {
      id: 'modules',
      action: async (stage) => {
        await stage.click(section(stage.page, 'extension'));
        await stage.page.waitForTimeout(2500);
        await stage.highlight('main, .mat-drawer-content, body', 4000);
      },
    },
    {
      id: 'widgets',
      action: async (stage) => {
        await stage.click(section(stage.page, 'widgets'));
        await stage.page.waitForTimeout(2500);
        await stage.highlight('main, .mat-drawer-content, body', 4000);
      },
    },
    { id: 'outro', holdMs: 1000 },
  ],
};
