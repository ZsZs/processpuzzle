import { expect, type Locator, type Page } from '@playwright/test';
import type { BaseEntityAttrDescriptor, BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { attrSelector, buttonTestId, formControlLocator, formControlSelector } from '../selectors/selector.builder';
import { inputAttrs } from '../data/test-data-factory';
import { RouteResolver } from '../routing/route.resolver';

const TEXT_TYPES = new Set<string>(['TEXT_BOX', 'TEXTAREA']);
const READONLY_TYPES = new Set<string>(['FOREIGN_KEY']);
// TODO: ARTIFACT / LOOKUP / COMPONENTS — deferred, depends on linked-entity resolution
const DEFERRED_TYPES = new Set<string>(['ARTIFACT', 'LOOKUP', 'COMPONENTS']);

/** Compare two date strings by y/m/d, ignoring formatting differences (ISO vs locale-formatted). */
function sameCalendarDay(a: string, b: string): boolean {
  const da = new Date(a);
  const db = new Date(b);
  if (isNaN(da.getTime()) || isNaN(db.getTime())) return false;
  return da.getFullYear() === db.getFullYear() && da.getMonth() === db.getMonth() && da.getDate() === db.getDate();
}

export class EntityFormPO {
  constructor(
    private page: Page,
    private descriptor: BaseEntityDescriptor,
    private routes: RouteResolver,
  ) {}

  // ── Navigation ──────────────────────────────────────────────────

  async navigateToDetail(entityId: string) {
    await this.page.goto(this.routes.detailRoute(this.descriptor.entityName, entityId));
    await this.page.waitForURL(/\/details$/);
  }

  // ── Form interactions ───────────────────────────────────────────

  async fillForm(data: Record<string, string>) {
    for (const attr of inputAttrs(this.descriptor)) {
      const value = data[attr.attrName] ?? '';
      await this.fillControl(attr, value);
    }
  }

  private async fillControl(attr: BaseEntityAttrDescriptor, value: string) {
    const control = this.page.getByTestId(formControlSelector(this.descriptor.entityName, attr.attrName));
    const inner = control.locator(formControlLocator(attr.formControlType)).first();

    switch (attr.formControlType as string) {
      case 'TEXT_BOX':
      case 'TEXTAREA':
        await inner.fill('');
        await inner.pressSequentially(value, { delay: 50 });
        break;
      case 'CHECKBOX':
        if (value === 'true') await inner.check();
        else await inner.uncheck();
        break;
      case 'DATE':
        await inner.fill(value);
        await inner.blur();
        break;
      case 'DROPDOWN':
        await inner.click();
        await this.page.locator('mat-option').filter({ hasText: value }).first().click();
        break;
      case 'TAGS': {
        for (const token of value
          .split(',')
          .map((t) => t.trim())
          .filter(Boolean)) {
          await inner.pressSequentially(token, { delay: 30 });
          await inner.press('Enter');
        }
        break;
      }
      // FOREIGN_KEY: read-only in the form
      // ARTIFACT / LOOKUP / COMPONENTS: deferred
      default:
        break;
    }
  }

  /** Save navigates back to the list. */
  async save() {
    await this.page.getByTestId(buttonTestId(this.descriptor.entityName, 'save')).click();
    await this.page.waitForURL(/\/list$/);
  }

  async edit() {
    await this.page.getByTestId(buttonTestId(this.descriptor.entityName, 'edit')).click();
  }

  /** Delete navigates back to the list. */
  async delete() {
    await this.page.getByTestId(buttonTestId(this.descriptor.entityName, 'delete')).click();
    await this.page.waitForURL(/\/list$/);
  }

  // ── Assertions ──────────────────────────────────────────────────

  async assertFieldValues(data: Record<string, string>) {
    for (const attr of inputAttrs(this.descriptor)) {
      const value = data[attr.attrName] ?? '';
      await this.assertControlValue(attr, value);
    }
  }

  private async assertControlValue(attr: BaseEntityAttrDescriptor, value: string) {
    const control: Locator = this.page.locator(attrSelector(this.descriptor.entityName, attr.attrName));

    if (TEXT_TYPES.has(attr.formControlType)) {
      await expect(control.locator(formControlLocator(attr.formControlType)).first()).toHaveValue(value);
      return;
    }

    switch (attr.formControlType as string) {
      case 'DATE': {
        const input = control.locator(formControlLocator(attr.formControlType)).first();
        await expect(input).not.toHaveValue('');
        const actual = await input.inputValue();
        expect(sameCalendarDay(actual, value), `DATE ${attr.attrName}: expected ${value}, got "${actual}"`).toBe(true);
        break;
      }
      case 'CHECKBOX': {
        const cb = control.locator('input[type="checkbox"]').first();
        if (value === 'true') await expect(cb).toBeChecked();
        else await expect(cb).not.toBeChecked();
        break;
      }
      case 'DROPDOWN':
        await expect(control.locator('mat-select')).toContainText(value);
        break;
      case 'TAGS': {
        for (const token of value
          .split(',')
          .map((t) => t.trim())
          .filter(Boolean)) {
          await expect(control.locator('mat-chip-row').filter({ hasText: token }).first()).toBeVisible();
        }
        break;
      }
      default:
        if (READONLY_TYPES.has(attr.formControlType)) {
          await expect(control).toBeVisible();
        }
        if (DEFERRED_TYPES.has(attr.formControlType)) return;
        break;
    }
  }
}
