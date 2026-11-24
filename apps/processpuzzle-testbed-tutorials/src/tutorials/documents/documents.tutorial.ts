import type { APIRequestContext, Page } from '@playwright/test';
import type { Tutorial } from '../../recorder/tutorial';

/**
 * The Document Designer: a document is made of blocks of text, which the designer writes in place, and it is shown in a
 * preview. The slug is code and stays the same in every language; the title, author, description and the block's text
 * are what the scenario types, from the narration's values.
 *
 * Targets are `data-testid`s the generated screens carry, or Material icons, never captions.
 */
const SLUG = 'welcome-letter';

const field = (attribute: string) => `[data-testid="document-${attribute}"]`;
const documentsCard = (page: Page) => page.locator('pp-cards-grid mat-card').filter({ has: page.locator('mat-icon', { hasText: 'article' }) });
const documentRow = (page: Page, title: string) => page.locator('tr.mat-mdc-row a', { hasText: title }).first();

/** Removes the document a previous run made, found by its slug. */
async function removeDocument(request: APIRequestContext, root: string): Promise<void> {
  const response = await request.get(`${root}/documents?size=200`);
  if (response.status() !== 200) throw new Error(`listing documents answered ${response.status()}: ${await response.text()}`);
  const { content = [] } = (await response.json()) as { content?: { id: string; slug: string }[] };
  for (const document of content.filter((candidate) => candidate.slug === SLUG)) {
    const removed = await request.delete(`${root}/documents/${encodeURIComponent(document.id)}`);
    if (![200, 204, 404].includes(removed.status())) {
      throw new Error(`removing the '${SLUG}' document answered ${removed.status()}: ${await removed.text()}`);
    }
  }
}

export const DOCUMENTS: Tutorial = {
  name: 'documents',
  startPath: '/design',
  ready: (page) => documentsCard(page),

  reset: async ({ request, root }) => removeDocument(request, root),

  steps: [
    { id: 'intro', action: (stage) => stage.highlight(documentsCard(stage.page), 3000) },
    {
      id: 'open',
      action: async (stage) => {
        await stage.click(documentsCard(stage.page).locator('mat-card-actions button'));
        await stage.page.locator('table').first().waitFor({ state: 'visible' });
      },
    },
    { id: 'list', action: (stage) => stage.highlight('table', 3500) },
    {
      id: 'new',
      action: async (stage) => {
        await stage.click(field('new'));
        await stage.page.locator(field('slug')).waitFor({ state: 'visible' });
      },
    },
    {
      id: 'identify',
      action: async (stage) => {
        await stage.type(`${field('slug')} input`, SLUG);
        await stage.type(`${field('title')} input`, stage.values['documentTitle']);
        await stage.type(`${field('author')} input`, stage.values['documentAuthor']);
        await stage.type(`${field('description')} textarea`, stage.values['documentDescription']);
      },
    },
    {
      id: 'save-document',
      action: async (stage) => {
        await stage.click(field('save'));
        await documentRow(stage.page, stage.values['documentTitle']).waitFor({ state: 'visible' });
      },
    },
    {
      id: 'open-content',
      action: async (stage) => {
        await documentRow(stage.page, stage.values['documentTitle']).click();
        await stage.click(field('show-content'));
        await stage.page.locator('div.ProseMirror, [data-testid="document-add-text-block"]').first().waitFor({ state: 'visible' });
      },
    },
    {
      id: 'add-text',
      action: async (stage) => {
        await stage.click(field('add-text-block'));
        await stage.click('div.ProseMirror');
        // The editor keeps what is written as it is written, so there is no save to wait for.
        await stage.page.keyboard.type(stage.values['blockText'], { delay: 25 });
        await stage.page.waitForTimeout(1500);
      },
    },
    {
      id: 'preview',
      action: async (stage) => {
        await stage.click(field('show-preview'));
        await stage.page.waitForTimeout(1500);
        await stage.highlight('main, .mat-drawer-content, body', 3500);
      },
    },
    { id: 'outro', holdMs: 1000 },
  ],
};
