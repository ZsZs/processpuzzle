import { expect, test, type Page } from '@playwright/test';

/**
 * The context object of the Base Entity samples: "Demo Root" is the aggregate root, "Demo Component One" a kind of
 * part it embeds. The three ways a user meets the context object are tested here; what the entity forms do once
 * opened is the CRUD suites' business and is deliberately not repeated.
 *
 * Each test is its own browser context, so the remembered context object (localStorage) starts empty — "the page is
 * refreshed" is a fresh `goto`. The seeded roots are create-only, so these tests read them and change nothing.
 */
const ROOT_LIST_PATH = '/base-entity/samples/demo-root/list';
const ALPHA = 'Demo Root Alpha';
const BETA = 'Demo Root Beta';
const PART_LIST = /\/demo-root\/([^/]+)\/demo-component-one\/list$/;

test.describe('[ContextObject] SCENARIOS', () => {
  test('1. selecting the context upfront is remembered, and the part list is about that object', async ({ page }) => {
    await openRootList(page);

    await tick(page, ALPHA);
    await page.getByTestId('demoRoot-set-active-context').click();

    // Only remembered: the user stays on the list, and the sample says which object it is.
    await expect(page).toHaveURL(/\/demo-root\/list$/);
    await expect(page.getByTestId('context-object-banner')).toContainText(ALPHA);

    await page.getByRole('radio', { name: 'Embedded Component One' }).click();

    await expect(page).toHaveURL(PART_LIST);
    await expect(rowOf(page, 'Alpha one A')).toBeVisible();
    await expect(rowOf(page, 'Alpha one B')).toBeVisible();
    await expect(rowOf(page, 'Beta one A')).toHaveCount(0);
  });

  test('2. opening a part list without a context object asks for one, then continues to the list', async ({ page }) => {
    await openRootList(page);

    await page.getByRole('radio', { name: 'Embedded Component One' }).click();

    // Sent to the aggregate's list, which says where the user was heading.
    await expect(page).toHaveURL(/\/demo-root\/list$/);
    await expect(page.getByTestId('context-object-hint')).toBeVisible();
    await expect(page.getByTestId('demoRoot-set-active-context')).toBeDisabled();

    await tick(page, BETA);
    await page.getByTestId('demoRoot-set-active-context').click();

    await expect(page).toHaveURL(PART_LIST);
    await expect(rowOf(page, 'Beta one A')).toBeVisible();
    await expect(rowOf(page, 'Alpha one A')).toHaveCount(0);
    await expect(page.getByTestId('context-object-banner')).toContainText(BETA);
  });

  test('3. editing the aggregate root opens its details, by the Edit button and by its name link', async ({ page }) => {
    await openRootList(page);
    await expect(page.getByTestId('demoRoot-edit')).toBeDisabled();

    await tick(page, ALPHA);
    await expect(page.getByTestId('demoRoot-edit')).toBeEnabled();
    await page.getByTestId('demoRoot-edit').click();
    await expect(page).toHaveURL(/\/demo-root\/[^/]+\/details$/);

    // The name link is a way in of its own: it must not select the row it sits on.
    await openRootList(page);
    await rowOf(page, BETA).locator('a.link-to-details').click();
    await expect(page).toHaveURL(/\/demo-root\/[^/]+\/details$/);
    await page.goBack();
    await expect(rowOf(page, BETA).getByRole('checkbox')).not.toBeChecked();
  });
});

async function openRootList(page: Page): Promise<void> {
  await page.goto(ROOT_LIST_PATH);
  // The list loads its rows from the backend after a lazy chunk and the metadata definition: slower than the default budget on a cold stack.
  await expect(rowOf(page, ALPHA)).toBeVisible({ timeout: 30_000 });
}

/** A row of the list on screen, found by the text it shows. */
function rowOf(page: Page, text: string) {
  return page.locator('tr.mat-mdc-row').filter({ hasText: text });
}

/** Selects the row with its checkbox — the only thing that selects. */
async function tick(page: Page, text: string): Promise<void> {
  await rowOf(page, text).getByRole('checkbox').check();
}
