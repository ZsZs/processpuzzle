import type { Page } from '@playwright/test';
import type { Tutorial } from '../../recorder/tutorial';

/**
 * The Release screen: what would go live, the problems that stand in the way, and the step that makes it live. The scenario
 * explains each part and stops short of Publish, which would release the testbed; nothing it does changes a definition, so
 * it needs no reset. The definitions it names are data, the same in every language.
 */
const card = (page: Page) => page.locator('pp-cards-grid mat-card').filter({ has: page.locator('mat-icon', { hasText: 'rocket_launch' }) });

export const RELEASE: Tutorial = {
  name: 'release',
  startPath: '/design',
  ready: (page) => card(page),

  steps: [
    { id: 'intro', action: (stage) => stage.highlight(card(stage.page), 4000) },
    {
      id: 'open',
      action: async (stage) => {
        await stage.click(card(stage.page).locator('mat-card-actions button'));
        await stage.page.locator('[data-testid="release-changes"]').waitFor({ state: 'visible' });
      },
    },
    { id: 'pending', action: (stage) => stage.highlight('[data-testid="release-changes"]', 4500) },
    {
      id: 'changes',
      action: async (stage) => {
        await stage.highlight('[data-testid="release-change-entity-product"]', 3000);
        await stage.highlight('[data-testid="release-change-module-order-admin"]', 3000);
        await stage.highlight('[data-testid="release-change-app-demo"]', 3000);
        await stage.highlight('[data-testid="release-change-app-order-desk"]', 3000);
      },
    },
    { id: 'problems', action: (stage) => stage.highlight('[data-testid="release-problems"]', 5000) },
    { id: 'acknowledge', action: (stage) => stage.highlight('[data-testid="release-acknowledge"]', 4000) },
    { id: 'publish', action: (stage) => stage.highlight('[data-testid="release-publish"]', 4500) },
    { id: 'history', action: (stage) => stage.highlight('[data-testid="release-no-history"]', 4000) },
    { id: 'outro', holdMs: 1000 },
  ],
};
