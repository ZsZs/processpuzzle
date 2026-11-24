import type { Page } from '@playwright/test';
import type { Stage, Tutorial } from '../../recorder/tutorial';

/**
 * The Entities card: define an entity, give it two attributes, and see that it stays a draft until it is released.
 *
 * Every target is a `data-testid` the generated screens already carry (`<entityName>-<attrName>`, the ones the e2e
 * suites drive) or a structural selector — never a caption, because the same scenario is recorded in five languages.
 */
const ENTITY_CODE = 'product';

const control = (entity: 'entityDefinition' | 'entityAttribute', attribute: string) => `[data-testid="${entity}-${attribute}"]`;
const definition = (attribute: string) => control('entityDefinition', attribute);
const attribute = (name: string) => control('entityAttribute', name);

const entitiesCard = (page: Page) => page.locator('pp-cards-grid mat-card').filter({ has: page.locator('mat-icon', { hasText: 'checkbook' }) });
const attributesBox = `${definition('attributes')} fieldset`;

/** A `mat-select` of the generated form. Its options are codes (`TEXT`, `NUMBER`), the same in every language. */
async function choose(stage: Stage, selector: string, option: string): Promise<void> {
  await stage.click(selector);
  await stage.click(stage.page.getByRole('option', { name: option, exact: true }));
}

/** The Add button of an embedded list shows only while its box has the focus. */
async function addAttribute(stage: Stage): Promise<void> {
  await stage.click(`${attributesBox} legend`);
  await stage.click(`${attributesBox} button.base-entity-form-focus-action`);
  await stage.page.locator(attribute('code')).waitFor({ state: 'visible' });
  // The form is filled from the store a moment after it appears; typing before that is overwritten.
  await stage.page.waitForTimeout(1500);
}

async function saveAttribute(stage: Stage): Promise<void> {
  await stage.click(attribute('save'));
  await stage.page.waitForURL(new RegExp(`/${ENTITY_CODE}/details$`));
  await stage.page.locator(attributesBox).waitFor({ state: 'visible' });
}

/** Opens the product's own form from the list, through the filter. */
async function openProduct(stage: Stage): Promise<void> {
  await stage.type(definition('filter'), ENTITY_CODE);
  await stage.click(stage.page.locator('tr.mat-mdc-row a').filter({ hasText: new RegExp(`^\\s*${ENTITY_CODE}\\s*$`) }));
  await stage.page.locator(attributesBox).waitFor({ state: 'visible' });
}

export const ENTITY_DESIGNER: Tutorial = {
  name: 'entity-designer',
  startPath: '/design',
  ready: (page) => entitiesCard(page),

  reset: async ({ request, root }) => {
    const response = await request.delete(`${root}/entity-definitions/${ENTITY_CODE}`);
    if (![200, 204, 404].includes(response.status())) {
      throw new Error(`removing the '${ENTITY_CODE}' definition answered ${response.status()}: ${await response.text()}`);
    }
  },

  steps: [
    { id: 'intro', action: (stage) => stage.highlight(entitiesCard(stage.page), 3000) },
    {
      id: 'open',
      action: async (stage) => {
        await stage.click(entitiesCard(stage.page).locator('mat-card-actions button'));
        await stage.page.locator('base-list tr.mat-mdc-row').first().waitFor({ state: 'visible' });
      },
    },
    { id: 'list', action: (stage) => stage.highlight('base-list table', 3500) },
    {
      id: 'new',
      action: async (stage) => {
        await stage.click(definition('new'));
        await stage.page.locator(definition('code')).waitFor({ state: 'visible' });
      },
    },
    {
      id: 'identify',
      action: async (stage) => {
        await stage.type(`${definition('code')} input`, ENTITY_CODE);
        await stage.type(`${definition('name')} input`, stage.values['entityName']);
        await stage.type(`${definition('description')} textarea`, stage.values['entityDescription']);
      },
    },
    {
      id: 'save-definition',
      action: async (stage) => {
        await stage.click(definition('save'));
        await stage.page.waitForURL(/\/list$/);
      },
    },
    { id: 'find', action: (stage) => openProduct(stage) },
    { id: 'add-attribute', action: addAttribute },
    {
      id: 'describe-attribute',
      action: async (stage) => {
        await stage.type(`${attribute('code')} input`, 'name');
        await stage.type(`${attribute('name')} input`, stage.values['nameAttribute']);
        await choose(stage, attribute('valueKind'), 'TEXT');
        await stage.click(`${attribute('required')} input[type="checkbox"]`);
        await stage.click(`${attribute('isLinkToDetails')} input[type="checkbox"]`);
      },
    },
    { id: 'save-attribute', action: saveAttribute },
    {
      id: 'save-entity',
      action: async (stage) => {
        await stage.highlight(attributesBox, 1500);
        await stage.click(definition('save'));
        await stage.page.waitForURL(/\/list$/);
      },
    },
    {
      // The second attribute is added through the API, not typed: a UI-staged attribute is only stored by the entity's
      // own Save, and typing it on screen was the step that stalled. The form is reopened afterwards, so it shows the
      // stored list.
      id: 'second-attribute',
      action: async (stage) => {
        await openProduct(stage);
        const response = await stage.backend.request.post(`${stage.backend.root}/entity-definitions/${ENTITY_CODE}/attributes`, {
          data: {
            code: 'price',
            name: stage.values['priceAttribute'],
            valueKind: 'NUMBER',
            formControlType: 'NUMBER',
          },
        });
        if (response.status() !== 201) throw new Error(`adding the price attribute answered ${response.status()}: ${await response.text()}`);
        await stage.page.reload();
        await stage.page.locator(attribute('code')).first().waitFor({ state: 'attached' }).catch(() => undefined);
        await stage.highlight(attributesBox, 2500);
        // Back to the list, where the release step starts, as Save and Cancel both lead there.
        await stage.click(definition('cancel'));
        await stage.page.waitForURL(/\/list$/);
      },
    },
    {
      id: 'release',
      action: async (stage) => {
        await stage.type(definition('filter'), ENTITY_CODE);
        await stage.highlight(stage.page.locator('tr.mat-mdc-row').filter({ has: stage.page.locator('a', { hasText: new RegExp(`^\\s*${ENTITY_CODE}\\s*$`) }) }), 4000);
      },
    },
    { id: 'outro', holdMs: 1000 },
  ],
};
