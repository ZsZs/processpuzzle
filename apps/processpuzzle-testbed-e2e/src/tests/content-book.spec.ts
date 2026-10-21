import { expect, test, type Page } from '@playwright/test';

/**
 * The `content-book` widget on the Content tab of the seeded handbook, which no generated suite reaches: the
 * CRUD suites drive the ContentBook list and form, but the tree, the toolbar and the page it opens are a
 * widget's behaviour rather than anything a descriptor declares.
 *
 * Read-only on purpose. The handbook is a seeded, create-only book (SampleContentBookLoader never touches it
 * again), so a test that rearranged its tree would leave every later run looking at a different book.
 */
const LIST_PATH = '/base-document/samples/content-book/list';
const HANDBOOK_SLUG = 'testbed-handbook';
const HANDBOOK_TITLE = 'Testbed Handbook';
const OVERVIEW_TITLE = 'ProcessPuzzle Platform Overview';
const OVERVIEW_SLUG = 'platform-overview';

test.describe('[ContentBook] CONTENT', () => {
  test('opens the book from the slug in the list and shows a reader its toolbar instead of the status bar', async ({ page }) => {
    await openHandbookContent(page);

    const toolbar = page.getByTestId('content-book-toolbar');
    await expect(toolbar).toContainText(HANDBOOK_TITLE);
    await expect(page.locator('base-entity-statusbar mat-toolbar')).toHaveCount(0);
    // The signed-in e2e user holds no `documentation-editor` role, so the book's editor controls stay hidden.
    for (const testId of ['content-book-page-edit', 'content-book-delete', 'content-book-add-before', 'content-book-add-after', 'content-book-pdf']) {
      await expect(toolbar.getByTestId(testId)).toHaveCount(0);
    }
  });

  test('stays on the Content tab while navigating the tree', async ({ page }) => {
    await openHandbookContent(page);

    await page.locator('button.pp-tree-nav__label').filter({ hasText: OVERVIEW_TITLE }).click();

    // The slug in the URL is what makes a page shareable; the URL staying on `content` is the tab staying put.
    await expect(page).toHaveURL(new RegExp(`/content\\?(.*&)?doc=${OVERVIEW_SLUG}(&|$)`));
    await expect(page.getByTestId('content-book-page').locator('pp-content-book-document-view')).toBeVisible();
    await expect(page.getByTestId('content-book-toolbar')).toBeVisible();
  });
});

async function openHandbookContent(page: Page): Promise<void> {
  await page.goto(LIST_PATH);
  // The slug is a `link-to-details` anchor without an href, which has no `link` role — so it is found by class.
  await page.locator('a.link-to-details').filter({ hasText: new RegExp(`^\\s*${HANDBOOK_SLUG}\\s*$`) }).click();
  // The tab bar names each tab after the entity: "Content book - content".
  await page.getByRole('tab', { name: /content book - content/i }).click();
  await expect(page.getByTestId('content-book-toolbar')).toBeVisible();
}
