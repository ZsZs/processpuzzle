import type { Page } from '@playwright/test';
import type { Tutorial } from '../../recorder/tutorial';

/**
 * The Chat Designer, shown on two seeded chat definitions: order-discussion, one conversation per order, and order-topics,
 * many conversations for the same order. The scenario only reads; nothing it does changes a definition, so it needs no
 * reset. Chat definitions and their keys are data and codes, the same in every language.
 */
const CARD_ICON = 'forum';

const card = (page: Page) => page.locator('pp-cards-grid mat-card').filter({ has: page.locator('mat-icon', { hasText: CARD_ICON }) });
const definitionRow = (page: Page, key: string) => page.locator('tr.mat-mdc-row a', { hasText: new RegExp(`^\\s*${key}\\s*$`) }).first();
const tab = (name: 'list' | 'details') => `[data-testid="chatDefinition-show-${name}"]`;

export const CHATS: Tutorial = {
  name: 'chats',
  startPath: '/design',
  ready: (page) => card(page),

  steps: [
    { id: 'intro', action: (stage) => stage.highlight(card(stage.page), 4000) },
    {
      id: 'open',
      action: async (stage) => {
        await stage.click(card(stage.page).locator('mat-card-actions button'));
        await definitionRow(stage.page, 'order-discussion').waitFor({ state: 'visible' });
      },
    },
    { id: 'list', action: (stage) => stage.highlight('base-list table', 4000) },
    {
      id: 'definition',
      action: async (stage) => {
        await definitionRow(stage.page, 'order-discussion').click();
        await stage.page.locator('[data-testid="chatDefinition-name"]').waitFor({ state: 'visible' });
        await stage.highlight('[data-testid="chatDefinition-name"]', 3000);
        await stage.highlight('[data-testid="chatDefinition-description"]', 3500);
      },
    },
    {
      id: 'subject',
      action: async (stage) => {
        await stage.highlight('[data-testid="chatDefinition-subjectType"]', 3000);
        await stage.highlight('[data-testid="chatDefinition-subjectDefinition"]', 3500);
      },
    },
    {
      id: 'multiplicity',
      action: async (stage) => {
        await stage.highlight('[data-testid="chatDefinition-multiplicity"]', 4000);
      },
    },
    {
      id: 'lifecycle',
      action: async (stage) => {
        await stage.highlight('[data-testid="chatDefinition-lifecycle"]', 3000);
        await stage.highlight('[data-testid="chatDefinition-writableStates"]', 4000);
      },
    },
    {
      id: 'roles',
      action: async (stage) => {
        await stage.highlight('[data-testid="chatDefinition-readRoles"]', 3000);
        await stage.highlight('[data-testid="chatDefinition-writeRoles"]', 3000);
        await stage.highlight('[data-testid="chatDefinition-createRoles"]', 3500);
      },
    },
    {
      id: 'retention',
      action: async (stage) => {
        await stage.highlight('[data-testid="chatDefinition-includeSubjectOwner"]', 3000);
        await stage.highlight('[data-testid="chatDefinition-retentionDays"]', 3500);
      },
    },
    {
      id: 'compare',
      action: async (stage) => {
        await stage.click(tab('list'));
        await definitionRow(stage.page, 'order-topics').click();
        await stage.page.locator('[data-testid="chatDefinition-multiplicity"]').waitFor({ state: 'visible' });
        await stage.highlight('[data-testid="chatDefinition-multiplicity"]', 4500);
      },
    },
    { id: 'outro', holdMs: 1000 },
  ],
};
