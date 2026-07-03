import { expect, type Locator, type Page } from '@playwright/test';
import type { BaseEntityAttrDescriptor, BaseEntityDescriptor, FormControlType, Selectable } from '@processpuzzle/base-entity';
import { RouteResolver } from '../routing/route.resolver';
import { formControlTestId, toTestId } from '../selectors/test-id';

export interface LinkedEntityFixture {
  entityName: string;
  id: string;
  data: Record<string, string>;
  displayValue: string;
}

export interface ControlDataContext {
  descriptor: BaseEntityDescriptor;
  descriptorMap: Map<string, BaseEntityDescriptor>;
  createdIdsByEntity?: Record<string, string>;
  createdDataByEntity?: Record<string, Record<string, string>>;
  linkedFixturesByAttr?: Record<string, LinkedEntityFixture>;
  linkedDisplayValuesByAttr?: Record<string, string>;
  uniqueSuffix?: string;
}

export interface ControlInteractionContext extends ControlDataContext {
  page: Page;
  routes: RouteResolver;
  expectTimeoutMs?: number;
}

export interface FillControlOptions {
  /** Skip linked-entity controls during update flows that preserve existing relationships. */
  skipLinked?: boolean;
}

export function linkedFixtureAttrKey(entityName: string, attrName: string): string {
  return `${entityName}.${attrName}`;
}

function sameCalendarDay(a: string, b: string): boolean {
  const da = new Date(a);
  const db = new Date(b);
  if (isNaN(da.getTime()) || isNaN(db.getTime())) return false;
  return da.getFullYear() === db.getFullYear() && da.getMonth() === db.getMonth() && da.getDate() === db.getDate();
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function expectOptions(context: ControlInteractionContext): { timeout?: number } | undefined {
  return context.expectTimeoutMs === undefined ? undefined : { timeout: context.expectTimeoutMs };
}

function resolveSelectables(attr: BaseEntityAttrDescriptor): Array<Selectable> | undefined {
  if (typeof attr.getSelectables === 'function') return attr.getSelectables();
  const raw = attr.selectables;
  if (raw === undefined) return undefined;
  return typeof raw === 'function' ? raw() : raw;
}

export abstract class ControlTester {
  readonly isInput: boolean = true;
  readonly isLinked: boolean = false;
  readonly fillOrder: number = 10;

  constructor(readonly attr: BaseEntityAttrDescriptor) {}

  abstract innerLocator(): string;

  createValue(context: ControlDataContext): string {
    void context;
    return '';
  }

  updateValue(context: ControlDataContext, original: Record<string, string>): string {
    return original[this.attr.attrName] ?? '';
  }

  displayValue(context: ControlDataContext, value: string): string {
    return value;
  }

  linkedEntityName(): string | undefined {
    return this.attr.linkedEntityType;
  }

  protected control(page: Page, descriptor: BaseEntityDescriptor): Locator {
    return page.getByTestId(formControlTestId(descriptor.entityName, this.attr.attrName));
  }

  protected inner(page: Page, descriptor: BaseEntityDescriptor): Locator {
    return this.control(page, descriptor).locator(this.innerLocator()).first();
  }

  async fill(context: ControlInteractionContext, value: string, options: FillControlOptions = {}): Promise<void> {
    void context;
    void value;
    void options;
  }

  async assertValue(context: ControlInteractionContext, value: string): Promise<void> {
    await expect(this.inner(context.page, context.descriptor)).toHaveValue(this.displayValue(context, value), expectOptions(context));
  }

  protected linkedFixture(context: ControlDataContext): LinkedEntityFixture | undefined {
    const linkedName = this.linkedEntityName();
    if (!linkedName) return undefined;

    return (
      context.linkedFixturesByAttr?.[linkedFixtureAttrKey(context.descriptor.entityName, this.attr.attrName)] ??
      context.linkedFixturesByAttr?.[this.attr.attrName] ??
      (context.createdIdsByEntity?.[linkedName] && context.createdDataByEntity?.[linkedName]
        ? {
            entityName: linkedName,
            id: context.createdIdsByEntity[linkedName],
            data: context.createdDataByEntity[linkedName],
            displayValue: '',
          }
        : undefined)
    );
  }
}

class TextBoxControlTester extends ControlTester {
  innerLocator(): string {
    return 'input, textarea';
  }

  override createValue(context: ControlDataContext): string {
    if (this.inputType() === 'number') return '123';

    const suffix = context.uniqueSuffix ? ` ${context.uniqueSuffix}` : '';
    return `Test ${this.attr.label ?? this.attr.attrName}${suffix}`;
  }

  override updateValue(context: ControlDataContext, original: Record<string, string>): string {
    void context;
    if (this.inputType() === 'number') return String(Number(original[this.attr.attrName] ?? 0) + 1);

    return `Updated ${original[this.attr.attrName] ?? this.attr.attrName}`;
  }

  override async fill(context: ControlInteractionContext, value: string): Promise<void> {
    const inner = this.inner(context.page, context.descriptor);
    await inner.fill(value);
  }

  private inputType(): string {
    return (this.attr.options as { inputType?: string } | undefined)?.inputType ?? 'text';
  }
}

class TextAreaControlTester extends TextBoxControlTester {
  override createValue(context: ControlDataContext): string {
    const suffix = context.uniqueSuffix ? ` ${context.uniqueSuffix}` : '';
    return `Description for ${context.descriptor.entityName}${suffix}`;
  }
}

class CheckboxControlTester extends ControlTester {
  innerLocator(): string {
    return 'input[type="checkbox"]';
  }

  override createValue(context: ControlDataContext): string {
    void context;
    return 'true';
  }

  override updateValue(context: ControlDataContext, original: Record<string, string>): string {
    void context;
    return original[this.attr.attrName] === 'true' ? 'false' : 'true';
  }

  override async fill(context: ControlInteractionContext, value: string): Promise<void> {
    const inner = this.inner(context.page, context.descriptor);
    if (value === 'true') await inner.check();
    else await inner.uncheck();
  }

  override async assertValue(context: ControlInteractionContext, value: string): Promise<void> {
    const checkbox = this.inner(context.page, context.descriptor);
    if (value === 'true') await expect(checkbox).toBeChecked(expectOptions(context));
    else await expect(checkbox).not.toBeChecked(expectOptions(context));
  }
}

class DateControlTester extends ControlTester {
  innerLocator(): string {
    return 'input[matInput]';
  }

  override createValue(context: ControlDataContext): string {
    void context;
    return '2026-01-15';
  }

  override updateValue(context: ControlDataContext, original: Record<string, string>): string {
    void context;
    void original;
    return '2026-02-20';
  }

  override async fill(context: ControlInteractionContext, value: string): Promise<void> {
    const inner = this.inner(context.page, context.descriptor);
    await inner.fill(value);
    await inner.blur();
  }

  override async assertValue(context: ControlInteractionContext, value: string): Promise<void> {
    const input = this.inner(context.page, context.descriptor);
    await expect(input).not.toHaveValue('', expectOptions(context));
    const actual = await input.inputValue();
    expect(sameCalendarDay(actual, value), `DATE ${this.attr.attrName}: expected ${value}, got "${actual}"`).toBe(true);
  }
}

class DropdownControlTester extends ControlTester {
  innerLocator(): string {
    return 'mat-select';
  }

  override createValue(context: ControlDataContext): string {
    void context;
    return String(resolveSelectables(this.attr)?.[0]?.value ?? '');
  }

  override updateValue(context: ControlDataContext, original: Record<string, string>): string {
    void context;
    return String(resolveSelectables(this.attr)?.[1]?.value ?? original[this.attr.attrName] ?? '');
  }

  override async fill(context: ControlInteractionContext, value: string): Promise<void> {
    await this.inner(context.page, context.descriptor).click();
    await context.page.locator('mat-option').filter({ hasText: value }).first().click();
  }

  override async assertValue(context: ControlInteractionContext, value: string): Promise<void> {
    await expect(this.control(context.page, context.descriptor).locator('mat-select')).toContainText(value, expectOptions(context));
  }
}

class TagsControlTester extends ControlTester {
  innerLocator(): string {
    return 'mat-chip-grid input';
  }

  override createValue(context: ControlDataContext): string {
    void context;
    return 'alpha,beta';
  }

  override updateValue(context: ControlDataContext, original: Record<string, string>): string {
    void context;
    return `${original[this.attr.attrName] ?? ''},gamma`;
  }

  override async fill(context: ControlInteractionContext, value: string): Promise<void> {
    const inner = this.inner(context.page, context.descriptor);
    for (const token of this.tokens(value)) {
      await inner.pressSequentially(token, { delay: 30 });
      await inner.press('Enter');
    }
  }

  override async assertValue(context: ControlInteractionContext, value: string): Promise<void> {
    const control = this.control(context.page, context.descriptor);
    for (const token of this.tokens(value)) {
      await expect(control.locator('mat-chip-row').filter({ hasText: token }).first()).toBeVisible(expectOptions(context));
    }
  }

  private tokens(value: string): string[] {
    return value
      .split(',')
      .map((token) => token.trim())
      .filter(Boolean);
  }
}

class ForeignKeyControlTester extends ControlTester {
  override readonly isLinked = true;
  override readonly fillOrder = 0;

  innerLocator(): string {
    return 'input[matInput]';
  }

  override createValue(context: ControlDataContext): string {
    const fixture = this.linkedFixture(context);
    const linkedName = this.linkedEntityName();
    return fixture?.id ?? (linkedName ? (context.createdIdsByEntity?.[linkedName] ?? '') : '');
  }

  override displayValue(context: ControlDataContext, value: string): string {
    void value;
    const displayOverride = context.linkedDisplayValuesByAttr?.[this.attr.attrName];
    if (displayOverride) return displayOverride;

    const fixture = this.linkedFixture(context);
    if (fixture?.displayValue) return fixture.displayValue;

    const linkedName = this.linkedEntityName();
    if (!linkedName) return '';

    const linkedRow = context.createdDataByEntity?.[linkedName];
    const linkedDescriptor = context.descriptorMap.get(linkedName);
    const linkedIdentification = linkedDescriptor ? identificationAttrFromTesters(linkedDescriptor) : undefined;
    return linkedIdentification && linkedRow ? (linkedRow[linkedIdentification.attrName] ?? '') : '';
  }

  override async fill(context: ControlInteractionContext, value: string, options: FillControlOptions = {}): Promise<void> {
    if (options.skipLinked) return;

    const identificationValue = this.displayValue(context, value);
    const linkedName = this.linkedEntityName();
    if (!identificationValue || !linkedName) return;

    if (!context.descriptorMap.has(linkedName)) return;

    const originUrl = context.page.url();
    //    await this.inner(context.page, context.descriptor).click();
    await this.inner(context.page, context.descriptor).focus();
    await context.page.getByRole('button', { name: `Select ${linkedName}` }).click();
    await context.page.waitForURL(/\/list(\?|$)/);

    const filterInput = context.page.getByTestId(`${toTestId(linkedName)}-filter`);
    await filterInput.fill(identificationValue);
    await filterInput.dispatchEvent('keyup');

    const row = context.page
      .locator('mat-row')
      .filter({ has: context.page.locator('mat-cell').filter({ hasText: new RegExp(`^${escapeRegExp(identificationValue)}$`) }) })
      .first();
    await row.locator('mat-checkbox input[type="checkbox"]').first().check();
    await context.page.getByTestId(`${toTestId(linkedName)}-select`).click();

    await context.page.waitForURL(originUrl);
  }
}

class LookupControlTester extends ControlTester {
  override readonly isLinked = true;

  innerLocator(): string {
    return 'input[matInput]';
  }

  override createValue(context: ControlDataContext): string {
    const fixture = this.linkedFixture(context);
    if (fixture) return this.lookupKey(fixture.data) || fixture.id;

    const linkedName = this.linkedEntityName();
    const linkedRow = linkedName ? context.createdDataByEntity?.[linkedName] : undefined;
    return linkedRow ? this.lookupKey(linkedRow) : '';
  }

  override displayValue(context: ControlDataContext, value: string): string {
    void value;
    const displayOverride = context.linkedDisplayValuesByAttr?.[this.attr.attrName];
    if (displayOverride) return displayOverride;

    const fixture = this.linkedFixture(context);
    if (fixture) return this.lookupDisplayValue(fixture.data) || fixture.displayValue;

    const linkedName = this.linkedEntityName();
    const linkedRow = linkedName ? context.createdDataByEntity?.[linkedName] : undefined;
    return linkedRow ? this.lookupDisplayValue(linkedRow) : '';
  }

  override async fill(context: ControlInteractionContext, value: string, options: FillControlOptions = {}): Promise<void> {
    if (options.skipLinked) return;

    const displayValue = this.displayValue(context, value);
    if (!displayValue) return;

    const input = this.inner(context.page, context.descriptor);
    await input.fill('');
    await input.pressSequentially(displayValue, { delay: 20 });

    const option = context.page
      .getByRole('listbox', { name: this.attr.label ?? this.attr.attrName })
      .getByRole('option', { name: displayValue, exact: true })
      .first();
    await expect(option).toBeVisible(expectOptions(context));
    await option.click();
    await expect(input).toHaveValue(displayValue, expectOptions(context));
  }

  private lookupKey(row: Record<string, string>): string {
    return row['key'] ?? row['id'] ?? '';
  }

  private lookupDisplayValue(row: Record<string, string>): string {
    return row['value'] ?? row['key'] ?? '';
  }
}

class NoopControlTester extends ControlTester {
  override readonly isInput: boolean;

  constructor(attr: BaseEntityAttrDescriptor, isInput: boolean) {
    super(attr);
    this.isInput = isInput;
  }

  innerLocator(): string {
    return '';
  }

  override async assertValue(context: ControlInteractionContext, value: string): Promise<void> {
    void context;
    void value;
  }
}

export function createControlTester(attr: BaseEntityAttrDescriptor): ControlTester {
  switch (attr.formControlType as string) {
    case 'TEXT_BOX':
      return new TextBoxControlTester(attr);
    case 'TEXTAREA':
      return new TextAreaControlTester(attr);
    case 'CHECKBOX':
      return new CheckboxControlTester(attr);
    case 'DATE':
      return new DateControlTester(attr);
    case 'DROPDOWN':
      return new DropdownControlTester(attr);
    case 'TAGS':
      return new TagsControlTester(attr);
    case 'FOREIGN_KEY':
      return new ForeignKeyControlTester(attr);
    case 'LOOKUP':
      return new LookupControlTester(attr);
    case 'FLEX_BOX':
    case 'LABEL':
    case 'ARTIFACT':
    case 'COMPONENTS':
      return new NoopControlTester(attr, false);
    default:
      return new NoopControlTester(attr, true);
  }
}

export function controlTestersFor(descriptor: BaseEntityDescriptor): ControlTester[] {
  return (descriptor.attrDescriptors as BaseEntityAttrDescriptor[])
    .filter((attr) => attr.visible !== false && attr.disabled !== true)
    .map((attr) => createControlTester(attr))
    .filter((tester) => tester.isInput);
}

export function identificationAttrFromTesters(descriptor: BaseEntityDescriptor): BaseEntityAttrDescriptor | undefined {
  return controlTestersFor(descriptor).find((tester) => tester.attr.isLinkToDetails === true)?.attr;
}

export function formControlLocatorForType(type: FormControlType): string {
  return createControlTester({ formControlType: type } as BaseEntityAttrDescriptor).innerLocator();
}
