import type { Tutorial, TutorialContext } from './tutorial-recorder';

/**
 * Example scenario. All selectors are PLACEHOLDERS: add matching data-tour
 * attributes to the real UI (the same attributes can serve the in-app tours
 * and your E2E tests).
 */

// Shared across designers: open the "Designer - home" drop-down and pick one.
export async function openFromDesignerMenu(ctx: TutorialContext, designerId: string): Promise<void> {
  await ctx.click("[data-tour='designer-menu']");
  await ctx.click(`[data-tour='designer-menu-${designerId}']`);
  await ctx.page.waitForLoadState('networkidle');
}

const tutorial: Tutorial = {
  name: 'entity-designer',
  title: 'Entity Designer: define your own business objects',
  startPath: '/designer',

  // Not recorded. Default Keycloak login form ids; adjust if you use a custom theme.
  setup: async (page) => {
    await page.goto('/designer');
    await page.fill('#username', process.env['TUTORIAL_USER'] ?? '');
    await page.fill('#password', process.env['TUTORIAL_PASSWORD'] ?? '');
    await page.click('#kc-login');
    await page.waitForLoadState('networkidle');
  },

  steps: [
    {
      id: 'intro',
      narration:
        'Welcome to the Entity Designer. Entities describe the business objects your application works with, such as products, customers or races.',
    },
    {
      id: 'open-designer',
      narration: 'Open the Designer menu at the top and choose Entity Designer.',
      action: (ctx) => openFromDesignerMenu(ctx, 'entity'),
    },
    {
      id: 'entity-list',
      narration: 'This list shows every entity defined in your application. Imported starters appear here as well.',
      action: (ctx) => ctx.highlight("[data-tour='entity-list']", 2500),
    },
    {
      id: 'new-entity',
      narration: 'To create a new entity, click New and give it a name. We will call ours Product.',
      action: async (ctx) => {
        await ctx.click("[data-tour='entity-new']");
        await ctx.type("[data-tour='entity-name-input']", 'Product');
      },
    },
    {
      id: 'add-field',
      narration: 'Now add fields. Each field has a name and a type. Here we add a price as a decimal number.',
      action: async (ctx) => {
        await ctx.click("[data-tour='field-add']");
        await ctx.type("[data-tour='field-name-input']", 'price');
        await ctx.click("[data-tour='field-type-select']");
        await ctx.click("[data-tour='field-type-option-decimal']");
      },
    },
    {
      id: 'save',
      narration: 'Save the entity. It is now part of your definitions, and it becomes live with your next release.',
      action: (ctx) => ctx.click("[data-tour='entity-save']"),
      holdMs: 1500,
    },
    {
      id: 'outro',
      narration: 'Next, learn how to give your entity a life cycle with the State Designer.',
    },
  ],
};

export default tutorial;
