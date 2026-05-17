import { expect, Locator, Page } from '@playwright/test';
import { EntityDescriptor } from '../types/entity-descriptor.types';
import { buttonTestId, toTestId } from '../support/selector.builder';
import { identificationAttr } from '../support/test-data-factory';
import { listRoute } from '../support/route.resolver';

export class EntityListPO {
  constructor(
    private page: Page,
    private descriptor: EntityDescriptor,
  ) {}

  // ── Navigation ──────────────────────────────────────────────────

  async navigateTo() {
    await this.page.goto(listRoute(this.descriptor.entityName));
    await this.page.waitForURL(/\/list$/);
  }

  async clickNew() {
    await this.page.getByTestId(buttonTestId(this.descriptor.entityName, 'new')).click();
  }

  /** Type into the toolbar filter input. */
  async filter(value: string) {
    const input = this.page.getByTestId(`${toTestId(this.descriptor.entityName)}-filter`);
    await input.fill(value);
  }

  // ── Locators ────────────────────────────────────────────────────

  rows(): Locator {
    return this.page.locator('mat-row');
  }

  /** Anchor in the identification column (the only `<a>` Material renders per row). */
  private identificationLink(identificationValue: string): Locator {
    return this.page.locator('mat-row a').filter({ hasText: identificationValue });
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
    await this.identificationLink(identificationValue).first().click();
    await this.page.waitForURL(/\/details$/);
    const segments = new URL(this.page.url()).pathname.split('/');
    return segments[segments.length - 2];
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
    await expect(this.identificationLink(identificationValue).first()).toBeVisible();
  }

  async assertNotInList(identificationValue: string) {
    if (!identificationAttr(this.descriptor)) return;
    await expect(this.identificationLink(identificationValue)).toHaveCount(0);
  }
}
