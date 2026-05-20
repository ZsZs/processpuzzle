import { expect, type Locator, type Page } from '@playwright/test';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { buttonTestId, listCancelButtonTestId, listSelectButtonTestId, toTestId } from '../selectors/selector.builder';
import { identificationAttr } from '../data/test-data-factory';
import { RouteResolver } from '../routing/route.resolver';

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export class EntityListPO {
  constructor(
    private page: Page,
    private descriptor: BaseEntityDescriptor,
    private routes: RouteResolver,
  ) {}

  // ── Navigation ──────────────────────────────────────────────────

  async navigateTo() {
    await this.page.goto(this.routes.listRoute(this.descriptor.entityName));
    await this.page.waitForURL(/\/list$/);
  }

  async clickNew() {
    await this.page.getByTestId(buttonTestId(this.descriptor.entityName, 'new')).click();
  }

  /** Type into the toolbar filter input. */
  async filter(value: string) {
    const input = this.page.getByTestId(`${toTestId(this.descriptor.entityName)}-filter`);
    await input.fill(value);
    await input.dispatchEvent('keyup');
  }

  // ── Locators ────────────────────────────────────────────────────

  rows(): Locator {
    return this.page.locator('mat-row');
  }

  /** Anchor in the identification column (the only `<a>` Material renders per row). */
  private identificationLink(identificationValue: string): Locator {
    return this.page.locator('mat-row a').filter({ hasText: new RegExp(`^${escapeRegExp(identificationValue)}$`) });
  }

  findRowByIdentification(identificationValue: string): Locator {
    return this.rows()
      .filter({ has: this.identificationLink(identificationValue) })
      .first();
  }

  // ── Actions ─────────────────────────────────────────────────────

  /**
   * Click the identification-column link of the row matching the value,
   * wait for the details URL, and return the captured entity id.
   */
  async openDetailsByIdentification(identificationValue: string): Promise<string> {
    await this.filter(identificationValue);
    await this.identificationLink(identificationValue).first().click();
    await this.page.waitForURL(/\/details$/);
    const segments = new URL(this.page.url()).pathname.split('/');
    return segments[segments.length - 2];
  }

  /**
   * Toggle the mat-checkbox on the row whose identification cell matches.
   * Select-mode list (entered via SELECT_OR_CREATE) renders the identification
   * column as plain text, not an `<a>`, so we match by cell content.
   */
  async selectRowByIdentification(identificationValue: string) {
    await this.filter(identificationValue);
    const row = this.rows()
      .filter({ has: this.page.locator('mat-cell').filter({ hasText: new RegExp(`^${escapeRegExp(identificationValue)}$`) }) })
      .first();
    await row.locator('mat-checkbox input[type="checkbox"]').first().check();
  }

  /** Click the `{entityName}-select` button (SELECT_OR_CREATE round-trip). */
  async clickSelectButton() {
    await this.page.getByTestId(listSelectButtonTestId(this.descriptor.entityName)).click();
  }

  async deleteByIdentification(identificationValue: string) {
    await this.navigateTo();
    await this.selectRowByIdentification(identificationValue);

    const deleteButton = this.page.getByTestId(buttonTestId(this.descriptor.entityName, 'delete'));
    await expect(deleteButton).toBeEnabled();
    await deleteButton.click();
    await this.assertNotInList(identificationValue);
  }

  /** Click the `{entityName}-cancel` button. */
  async clickCancelButton() {
    await this.page.getByTestId(listCancelButtonTestId(this.descriptor.entityName)).click();
  }

  // ── Assertions ──────────────────────────────────────────────────

  async assertNotEmpty() {
    await expect(this.rows().first()).toBeVisible();
  }

  async assertNewButtonVisible() {
    await expect(this.page.getByTestId(buttonTestId(this.descriptor.entityName, 'new'))).toBeVisible();
  }

  async assertFilterVisible() {
    await expect(this.page.getByTestId(`${toTestId(this.descriptor.entityName)}-filter`)).toBeVisible();
  }

  async assertInList(identificationValue: string) {
    if (!identificationAttr(this.descriptor)) return;
    await this.filter(identificationValue);
    await expect(this.identificationLink(identificationValue).first()).toBeVisible();
  }

  async assertNotInList(identificationValue: string) {
    if (!identificationAttr(this.descriptor)) return;
    await this.filter(identificationValue);
    await expect(this.identificationLink(identificationValue)).toHaveCount(0);
  }
}
