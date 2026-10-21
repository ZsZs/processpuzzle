import { expect, Page } from '@playwright/test';
import { toTestId } from '@processpuzzle/e2e-testing';

/**
 * Hand-driven controls of a generated entity form, for the screens the descriptor-driven suites cannot reach:
 * the embedded forms of a workflow (start events, events), which are created and edited through the UI but are
 * not in the generated CRUD suites. Every control's host carries `data-testid="<entityName>-<attrName>"`.
 */

/** A text control: types into the input (or textarea) inside the host. */
export async function fillText(page: Page, controlId: string, value: string): Promise<void> {
  await page.getByTestId(controlId).locator('input, textarea').first().fill(value);
}

/** A `mat-select` whose options are fixed by the descriptor. */
export async function chooseOption(page: Page, controlId: string, option: string): Promise<void> {
  await page.getByTestId(controlId).click();
  await page.getByRole('option', { name: option, exact: true }).click();
}

/** A checkbox control. */
export async function setChecked(page: Page, controlId: string, checked: boolean): Promise<void> {
  const box = page.getByTestId(controlId).locator('input[type="checkbox"]').first();
  await (checked ? box.check() : box.uncheck());
}

/**
 * A foreign-key control: its own list opens in select mode, the row is ticked and the choice comes back to the
 * form. The unsaved form is kept across the round trip, which is what the generated suites rely on as well.
 */
export async function pickRelated(page: Page, controlId: string, linkedEntity: string, identification: string): Promise<void> {
  const originPath = withoutLanguage(new URL(page.url()).pathname);
  await page.getByTestId(controlId).locator('input[matInput]').focus();
  await page.getByRole('button', { name: `Select ${linkedEntity}` }).click();
  await page.waitForURL(/\/list(\?|$)/);

  const filter = page.getByTestId(`${toTestId(linkedEntity)}-filter`);
  await filter.fill(identification);
  await filter.dispatchEvent('keyup');
  const row = page.locator('tr.mat-mdc-row').filter({ has: page.locator('td.mat-mdc-cell').filter({ hasText: new RegExp(`^\\s*${identification}\\s*$`) }) });
  await row.first().locator('mat-checkbox input[type="checkbox"]').check();
  await page.getByTestId(`${toTestId(linkedEntity)}-select`).click();

  await page.waitForURL((url) => withoutLanguage(url.pathname) === originPath);
  await expect(page.getByTestId(controlId).locator('input[matInput]')).not.toHaveValue('');
}

/** The router adds the language (`/en/...`) after the first navigation, so a path is compared without it. */
function withoutLanguage(pathname: string): string {
  return pathname.replace(/^\/[a-z]{2}\//, '/');
}
