import { expect, test, type Page } from '@playwright/test';

/**
 * The `content-book` widget on the testbed's handbook page, which no generated suite reaches: the CRUD suites
 * drive the ContentBook list and form, but the tree, the sidenav takeover and the page it opens are a widget's
 * behaviour rather than anything a descriptor declares.
 *
 * Read-only on purpose. The handbook is a seeded, create-only book (SampleContentBookLoader never touches it
 * again), so a test that rearranged its tree would leave every later run looking at a different book.
 */
const HANDBOOK_PATH = '/base-document/samples/handbook';
const HANDBOOK_TITLE = 'Testbed Handbook';
const OVERVIEW_TITLE = 'ProcessPuzzle Platform Overview';
const OVERVIEW_SLUG = 'platform-overview';

test.describe('[ContentBook] HANDBOOK', () => {
  test('shows the book in the sidenav and opens a page from it', async ({ page }) => {
    await openHandbook(page);

    await expect(page.getByTestId('sidenav-content-title')).toHaveText(HANDBOOK_TITLE);
    const tree = page.getByTestId('sidenav-content-body');
    await expect(tree.locator('[data-testid^="tree-nav-node-"]').first()).toBeVisible();

    // Each node has two buttons named after the page: the expander and the label that selects it.
    await tree.locator('button.pp-tree-nav__label').filter({ hasText: OVERVIEW_TITLE }).click();

    // The slug in the URL is what makes a page shareable; the view below it is what the reader came for.
    await expect(page).toHaveURL(new RegExp(`[?&]doc=${OVERVIEW_SLUG}(&|$)`));
    await expect(page.getByTestId('content-book-page').locator('pp-content-book-document-view')).toBeVisible();
  });

  test('gives the sidenav back to the menu and takes it again', async ({ page }) => {
    await openHandbook(page);
    const toggle = page.getByTestId('sidenav-content-toggle');
    const tree = page.getByTestId('sidenav-content-body');

    await expect(tree).toBeVisible();
    await expect(page.locator('app-sidenav')).toHaveCount(0);

    await toggle.click();
    await expect(tree).toHaveCount(0);
    await expect(page.locator('app-sidenav')).toBeVisible();

    await toggle.click();
    await expect(tree).toBeVisible();
    await expect(page.locator('app-sidenav')).toHaveCount(0);
  });
});

async function openHandbook(page: Page): Promise<void> {
  await page.goto(HANDBOOK_PATH);
  await expect(page.getByTestId('sidenav-content-toggle')).toBeVisible();
}
