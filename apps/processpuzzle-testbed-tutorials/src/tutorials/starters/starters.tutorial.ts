import type { Page } from '@playwright/test';
import type { Tutorial } from '../../recorder/tutorial';

/**
 * The Starter Manager: business starters, which add a domain to the organization's application. The scenario explains the
 * screen and stops short of Install, so it changes nothing and needs no reset. The catalog it shows is whatever the
 * organization can reach; where it is empty, the video says so.
 */
const card = (page: Page) => page.locator('pp-cards-grid mat-card').filter({ has: page.locator('mat-icon', { hasText: 'inventory_2' }) });

export const STARTERS: Tutorial = {
  name: 'starters',
  startPath: '/design',
  ready: (page) => card(page),

  steps: [
    { id: 'intro', action: (stage) => stage.highlight(card(stage.page), 4000) },
    {
      id: 'open',
      action: async (stage) => {
        await stage.click(card(stage.page).locator('mat-card-actions button'));
        await stage.page.locator('[data-testid="starter-preview"]').waitFor({ state: 'visible' });
      },
    },
    { id: 'catalog', action: (stage) => stage.highlight('main, .mat-drawer-content, body', 5000) },
    { id: 'preview', action: (stage) => stage.highlight('[data-testid="starter-preview"]', 4500) },
    { id: 'installed', action: (stage) => stage.highlight('[data-testid="installed-starters-empty"]', 4000) },
    { id: 'outro', holdMs: 1000 },
  ],
};
