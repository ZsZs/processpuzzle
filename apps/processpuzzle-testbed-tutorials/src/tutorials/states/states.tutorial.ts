import type { APIRequestContext, Page } from '@playwright/test';
import type { Stage, Tutorial } from '../../recorder/tutorial';

/**
 * The State Designer: a state machine for the Product entity, saved as a draft before its states, then with its states.
 * The states are keys in code (NEW, ACTIVE, DISCONTINUED), the same in every language; their names and the machine's
 * name are what the scenario types, from the narration's values.
 *
 * Targets are `data-testid`s the generated forms carry, or Material icons, never captions.
 */
const ENTITY = 'product';
const STATE_ATTRIBUTE = 'status';
const STATES = [
  { key: 'NEW', name: 'stateNameNew', final: false },
  { key: 'ACTIVE', name: 'stateNameActive', final: false },
  { key: 'DISCONTINUED', name: 'stateNameDiscontinued', final: true },
] as const;

const field = (form: 'stateMachineDefinition' | 'stateMachineState', attribute: string) => `[data-testid="${form}-${attribute}"]`;
const statesCard = (page: Page) => page.locator('pp-cards-grid mat-card').filter({ has: page.locator('mat-icon', { hasText: 'flag_circle' }) });
const machineRow = (page: Page) => page.locator('tr.mat-mdc-row a', { hasText: new RegExp(`^\\s*${ENTITY}\\s*$`) });

/** The product entity the scenario needs: created with its name and status attributes when a run finds it missing. */
async function ensureProduct(request: APIRequestContext, root: string): Promise<void> {
  const found = await request.get(`${root}/entity-definitions/${ENTITY}`);
  if (found.status() === 200) return;
  const created = await request.post(`${root}/entity-definitions`, {
    data: {
      code: ENTITY,
      name: 'Product',
      description: 'Something the company sells.',
      status: 'ACTIVE',
      attributes: [
        { code: 'name', name: 'Name', displayOrder: 1, valueKind: 'TEXT', formControlType: 'TEXT_BOX', required: true, indexed: true, isLinkToDetails: true },
        { code: STATE_ATTRIBUTE, name: 'Status', displayOrder: 2, valueKind: 'TEXT', formControlType: 'TEXT_BOX', indexed: true },
      ],
    },
  });
  if (created.status() !== 201) throw new Error(`creating the '${ENTITY}' definition answered ${created.status()}: ${await created.text()}`);
}

/** Opens the machine's own details from the list. */
async function openMachine(stage: Stage): Promise<void> {
  await machineRow(stage.page).first().click();
  await stage.page.locator(field('stateMachineDefinition', 'states')).waitFor({ state: 'visible' });
}

/** Adds one state to the open machine and returns to its details, where the states list shows it. */
async function addState(stage: Stage, state: (typeof STATES)[number]): Promise<void> {
  await stage.click(`${field('stateMachineDefinition', 'states')} legend`);
  await stage.click(`${field('stateMachineDefinition', 'states')} button.base-entity-form-focus-action`);
  await stage.page.locator(field('stateMachineState', 'key')).waitFor({ state: 'visible' });
  // The form is filled from the store a moment after it appears; typing before that is overwritten. The key and name
  // are set rather than typed: a tooltip of the form sits over the key field, and keystrokes sent to it went to the page.
  await stage.page.waitForTimeout(1500);
  await stage.page.locator(`${field('stateMachineState', 'key')} input`).fill(state.key);
  await stage.page.locator(`${field('stateMachineState', 'name')} input`).fill(stage.values[state.name]);
  if (state.final) await stage.click(`${field('stateMachineState', 'isFinal')} input[type="checkbox"]`);
  await stage.click(field('stateMachineState', 'save'));
  await stage.page.waitForURL(/state-machine-definition\/[^/]+\/details$/);
}

export const STATES_TUTORIAL: Tutorial = {
  name: 'states',
  startPath: '/design',
  ready: (page) => statesCard(page),

  reset: async ({ request, root }) => {
    const removed = await request.delete(`${root}/state-machines/${ENTITY}`);
    if (![200, 204, 404].includes(removed.status())) {
      throw new Error(`removing the '${ENTITY}' state machine answered ${removed.status()}: ${await removed.text()}`);
    }
    await ensureProduct(request, root);
  },

  steps: [
    { id: 'intro', action: (stage) => stage.highlight(statesCard(stage.page), 3000) },
    {
      id: 'open',
      action: async (stage) => {
        await stage.click(statesCard(stage.page).locator('mat-card-actions button'));
        await stage.page.locator('base-list table, table').first().waitFor({ state: 'visible' });
      },
    },
    { id: 'list', action: (stage) => stage.highlight('base-list table', 3500) },
    {
      id: 'new',
      action: async (stage) => {
        await stage.click(field('stateMachineDefinition', 'new'));
        await stage.page.locator(field('stateMachineDefinition', 'entityName')).waitFor({ state: 'visible' });
      },
    },
    {
      id: 'identify',
      action: async (stage) => {
        await stage.type(`${field('stateMachineDefinition', 'entityName')} input`, ENTITY);
        await stage.type(`${field('stateMachineDefinition', 'name')} input`, stage.values['machineName']);
        await stage.type(`${field('stateMachineDefinition', 'description')} textarea`, stage.values['machineDescription']);
      },
    },
    {
      id: 'choose-attribute',
      action: async (stage) => {
        await stage.type(`${field('stateMachineDefinition', 'stateAttributeKey')} input`, STATE_ATTRIBUTE);
        await stage.type(`${field('stateMachineDefinition', 'initialStateKey')} input`, STATES[0].key);
      },
    },
    {
      id: 'save-machine',
      action: async (stage) => {
        await stage.click(field('stateMachineDefinition', 'save'));
        await stage.page.waitForURL(/\/list$/);
      },
    },
    { id: 'find', action: (stage) => openMachine(stage) },
    {
      id: 'add-states',
      action: async (stage) => {
        for (const state of STATES) await addState(stage, state);
      },
    },
    {
      id: 'save-states',
      action: async (stage) => {
        await stage.highlight(`${field('stateMachineDefinition', 'states')}`, 1500);
        await stage.click(field('stateMachineDefinition', 'save'));
        await stage.page.waitForURL(/\/list$/);
      },
    },
    { id: 'show-result', action: (stage) => stage.highlight(machineRow(stage.page).first(), 4000) },
    { id: 'outro', holdMs: 1000 },
  ],
};
