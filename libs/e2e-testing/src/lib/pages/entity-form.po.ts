import { expect, type Locator, type Page } from '@playwright/test';
import type { BaseEntityAttrDescriptor, BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { attrSelector, buttonTestId, formControlLocator, formControlSelector, selectButtonAriaLabel } from '../selectors/selector.builder';
import { inputAttrs } from '../data/test-data-factory';
import { RouteResolver } from '../routing/route.resolver';
import { EntityListPO } from './entity-list.po';

const TEXT_TYPES = new Set<string>(['TEXT_BOX', 'TEXTAREA']);
// TODO: ARTIFACT / COMPONENTS — deferred, depends on linked-entity resolution
const DEFERRED_TYPES = new Set<string>(['ARTIFACT', 'COMPONENTS']);

/** Compare two date strings by y/m/d, ignoring formatting differences (ISO vs locale-formatted). */
function sameCalendarDay(a: string, b: string): boolean {
  const da = new Date(a);
  const db = new Date(b);
  if (isNaN(da.getTime()) || isNaN(db.getTime())) return false;
  return da.getFullYear() === db.getFullYear() && da.getMonth() === db.getMonth() && da.getDate() === db.getDate();
}

export interface FillFormOptions {
  /** Skip linked-entity controls (FOREIGN_KEY, LOOKUP) — UPDATE flow preserves existing selection. */
  skipLinked?: boolean;
}

export class EntityFormPO {
  constructor(
    private page: Page,
    private descriptor: BaseEntityDescriptor,
    private routes: RouteResolver,
    private descriptorMap: Map<string, BaseEntityDescriptor> = new Map(),
  ) {}

  // ── Navigation ──────────────────────────────────────────────────

  async navigateToDetail(entityId: string) {
    await this.page.goto(this.routes.detailRoute(this.descriptor.entityName, entityId));
    await this.page.waitForURL(/\/details$/);
  }

  // ── Form interactions ───────────────────────────────────────────

  async fillForm(data: Record<string, string>, linkedIdentifications: Record<string, string> = {}, options: FillFormOptions = {}) {
    // FOREIGN_KEY round-trips navigate away and back, resetting the form — fill them first
    // so values typed afterwards survive until Save.
    const attrs = inputAttrs(this.descriptor);
    const orderedAttrs = [...attrs.filter((a) => a.formControlType === 'FOREIGN_KEY'), ...attrs.filter((a) => a.formControlType !== 'FOREIGN_KEY')];
    for (const attr of orderedAttrs) {
      const value = data[attr.attrName] ?? '';
      await this.fillControl(attr, value, linkedIdentifications, options);
    }
  }

  private async fillControl(
    attr: BaseEntityAttrDescriptor,
    value: string,
    linkedIdentifications: Record<string, string>,
    options: FillFormOptions,
  ) {
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
      case 'FOREIGN_KEY': {
        if (options.skipLinked) break;
        const identificationValue = linkedIdentifications[attr.attrName];
        if (!identificationValue) break;
        await this.selectForeignKey(attr, identificationValue);
        break;
      }
      case 'LOOKUP': {
        if (options.skipLinked) break;
        const identificationValue = linkedIdentifications[attr.attrName];
        if (!identificationValue) break;
        await this.selectLookup(inner, identificationValue);
        break;
      }
      // ARTIFACT / COMPONENTS: deferred
      default:
        break;
    }
  }

  /**
   * Drive the SELECT_OR_CREATE round-trip for a FOREIGN_KEY control:
   * focus → click "Select <LinkedEntity>" → tick row → click select → return to form.
   */
  private async selectForeignKey(attr: BaseEntityAttrDescriptor, identificationValue: string) {
    const linkedName = attr.linkedEntityType?.entityName;
    if (!linkedName) return;
    const linkedDescriptor = this.descriptorMap.get(linkedName);
    if (!linkedDescriptor) return;

    const originUrl = this.page.url();
    const control = this.page.getByTestId(formControlSelector(this.descriptor.entityName, attr.attrName));
    const input = control.locator('input[matInput]').first();

    // Trigger (focusin) so the "Select" button renders.
    await input.click();

    await this.page.getByRole('button', { name: selectButtonAriaLabel(linkedName) }).click();
    await this.page.waitForURL(/\/list(\?|$)/);

    const linkedList = new EntityListPO(this.page, linkedDescriptor, this.routes);
    await linkedList.selectRowByIdentification(identificationValue);
    await linkedList.clickSelectButton();

    await this.page.waitForURL(originUrl);
  }

  /**
   * Drive the mat-autocomplete flow for a LOOKUP control:
   * click input → autocomplete shows every linked row's `value` → click the matching option.
   */
  private async selectLookup(input: Locator, displayValue: string) {
    await input.click();
    await this.page.getByRole('option', { name: displayValue, exact: true }).first().click();
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

  async assertFieldValues(data: Record<string, string>, linkedIdentifications: Record<string, string> = {}) {
    for (const attr of inputAttrs(this.descriptor)) {
      const value = data[attr.attrName] ?? '';
      await this.assertControlValue(attr, value, linkedIdentifications);
    }
  }

  private async assertControlValue(attr: BaseEntityAttrDescriptor, value: string, linkedIdentifications: Record<string, string>) {
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
      case 'FOREIGN_KEY':
      case 'LOOKUP': {
        const expected = linkedIdentifications[attr.attrName] ?? '';
        await expect(control.locator('input[matInput]').first()).toHaveValue(expected);
        break;
      }
      default:
        if (DEFERRED_TYPES.has(attr.formControlType)) return;
        break;
    }
  }
}
