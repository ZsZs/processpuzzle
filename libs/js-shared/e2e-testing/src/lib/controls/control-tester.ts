import { expect, type Locator, type Page } from '@playwright/test';
import type { BaseEntityAttrDescriptor, BaseEntityDescriptor, FormControlType, Selectable } from '@processpuzzle/base-entity';
import { RouteResolver } from '../routing/route.resolver';
import { formControlTestId, toTestId } from '../selectors/test-id';
import { exactText } from '../selectors/text-match';

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
  if (Number.isNaN(da.getTime()) || Number.isNaN(db.getTime())) return false;
  return da.getFullYear() === db.getFullYear() && da.getMonth() === db.getMonth() && da.getDate() === db.getDate();
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

/**
 * The `index`-th dropdown option's stored value, as the form data carries it — a string.
 *
 * `Selectable.value` is declared `unknown`, so this admits only the primitives that have a text form a
 * `mat-option` could be matched by, rather than stringifying blindly. Anything else — an object, a function, a
 * symbol — would stringify into text no option carries (`[object Object]`, say) and make the test fail somewhere
 * far from the descriptor that caused it. Such a value is reported as absent, and the caller falls back the same
 * way it does for a dropdown with too few options.
 */
function selectableValue(attr: BaseEntityAttrDescriptor, index: number): string | undefined {
  const value = resolveSelectables(attr)?.[index]?.value;
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean' || typeof value === 'bigint') return String(value);
  return undefined;
}

export abstract class ControlTester {
  readonly isInput: boolean = true;
  readonly isLinked: boolean = false;
  readonly fillOrder: number = 10;

  constructor(readonly attr: BaseEntityAttrDescriptor) {}

  abstract innerLocator(): string;

  createValue(_context: ControlDataContext): string {
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

  async fill(_context: ControlInteractionContext, _value: string, _options: FillControlOptions = {}): Promise<void> {
    // intentionally empty: a control type with no fill of its own is left as the form rendered it
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

/**
 * A value the sentence-shaped fixture text cannot be: lower-cased, with every run of anything else collapsed
 * to a single dash. `Test Slug e2e-mspt0-document-r0` becomes `test-slug-e2e-mspt0-document-r0`.
 *
 * This is the one shape worth deriving, because it is the one the platform's patterned fields actually have —
 * a URL slug, a route key, an identifier. Generating a string for an arbitrary regular expression is a
 * different problem, and pretending to solve it would produce fixtures that fail far from their cause; see
 * {@link TextBoxControlTester.patternedValue}, which checks the result and says so instead.
 */
function toDashedToken(value: string): string {
  let token = '';
  let separatorPending = false;

  for (const character of value.toLowerCase()) {
    const isAlphaNumeric = (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9');
    if (!isAlphaNumeric) {
      separatorPending = token.length > 0;
      continue;
    }

    if (separatorPending) token += '-';
    token += character;
    separatorPending = false;
  }

  return token;
}

class TextBoxControlTester extends ControlTester {
  innerLocator(): string {
    return 'input, textarea';
  }

  override createValue(context: ControlDataContext): string {
    if (this.inputType() === 'number') return '123';

    const suffix = context.uniqueSuffix ? ` ${context.uniqueSuffix}` : '';
    return this.patternedValue(`Test ${this.attr.label ?? this.attr.attrName}${suffix}`);
  }

  override updateValue(_context: ControlDataContext, original: Record<string, string>): string {
    if (this.inputType() === 'number') return String(Number(original[this.attr.attrName] ?? 0) + 1);

    return this.patternedValue(`Updated ${original[this.attr.attrName] ?? this.attr.attrName}`);
  }

  /**
   * `value` where the attribute declares no pattern, its dashed form where it does.
   *
   * A patterned field is one the form itself rejects — `BaseEntityFormBuilder` applies
   * `Validators.pattern` — so a fixture that ignored the pattern would leave Save disabled and the test
   * would report a missing row rather than an invalid value. Throwing when even the dashed form does not
   * match keeps that honest: the suites cannot invent a value for every pattern, and the descriptor is where
   * the mismatch has to be fixed.
   */
  private patternedValue(value: string): string {
    const pattern = (this.attr as { pattern?: string }).pattern;
    if (!pattern) return value;

    const dashed = toDashedToken(value);
    if (!new RegExp(pattern).test(dashed)) {
      throw new Error(
        `[${this.attr.attrName}] the generated fixture value '${dashed}' does not satisfy the declared pattern ${pattern}. ` +
          `The generated suites derive only a dashed-token value for a patterned text box — give the attribute a pattern that shape satisfies, or drive it from a test of its own.`,
      );
    }
    return dashed;
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

  override createValue(_context: ControlDataContext): string {
    return 'true';
  }

  override updateValue(_context: ControlDataContext, original: Record<string, string>): string {
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

  override createValue(_context: ControlDataContext): string {
    return '2026-01-15';
  }

  override updateValue(_context: ControlDataContext, _original: Record<string, string>): string {
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

  override createValue(_context: ControlDataContext): string {
    return selectableValue(this.attr, 0) ?? '';
  }

  override updateValue(_context: ControlDataContext, original: Record<string, string>): string {
    return selectableValue(this.attr, 1) ?? original[this.attr.attrName] ?? '';
  }

  /**
   * Opened from the keyboard rather than by clicking.
   *
   * While a `mat-select` holds no value its label has not floated yet and sits over the middle of the
   * control, so a click there has the label as its hit target and Playwright refuses it — indefinitely, since
   * the element it was asked to click stays "visible, enabled and stable" throughout. The layout is not itself
   * a defect: a user's click on that label does open the select. `Enter` on the focused select is what a
   * keyboard user does, and it does not depend on which element owns a coordinate.
   */
  override async fill(context: ControlInteractionContext, value: string): Promise<void> {
    const select = this.inner(context.page, context.descriptor);
    await select.focus();
    await select.press('Enter');
    await expect(select).toHaveAttribute('aria-expanded', 'true', expectOptions(context));

    // The overlay panel this select owns, named by the `aria-controls` Material sets while it is open. Scoping
    // the option lookup to it keeps a neighbouring select's options out of the match: a panel animating shut
    // still has its options in the DOM, and `mat-option` is page-global, so an unscoped click can land on the
    // previous panel's leftover — leaving this select open with no value, and Save behind its backdrop.
    const panelId = await select.getAttribute('aria-controls');
    const panel = panelId ? context.page.locator(`[id="${panelId}"]`) : context.page.locator('.mat-mdc-select-panel');
    await panel.locator('mat-option').filter({ hasText: value }).first().click();

    await expect(select).toHaveAttribute('aria-expanded', 'false', expectOptions(context));
  }

  override async assertValue(context: ControlInteractionContext, value: string): Promise<void> {
    await expect(this.control(context.page, context.descriptor).locator('mat-select')).toContainText(value, expectOptions(context));
  }
}

class TagsControlTester extends ControlTester {
  innerLocator(): string {
    return 'mat-chip-grid input';
  }

  override createValue(_context: ControlDataContext): string {
    if (this.inputType() === 'number') return '200,201';
    return 'alpha,beta';
  }

  override updateValue(_context: ControlDataContext, original: Record<string, string>): string {
    if (this.inputType() === 'number') return `${original[this.attr.attrName] ?? ''},204`;
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

  private inputType(): string {
    return (this.attr.options as { inputType?: string } | undefined)?.inputType ?? 'text';
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

  override displayValue(context: ControlDataContext, _value: string): string {
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
      .filter({ has: context.page.locator('mat-cell').filter({ hasText: exactText(identificationValue) }) })
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

  override displayValue(context: ControlDataContext, _value: string): string {
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

/** The three to-many relationship control types, which differ in what a row *means*. */
export type RelationshipKind = 'RELATED_ENTITIES' | 'COMPONENTS' | 'EMBEDDED_COMPONENTS';

/**
 * A to-many relationship control: a `fieldset` listing rows, not a value the form can be filled with.
 *
 * `isInput` stays false, which keeps these controls out of `fillForm`, `assertFieldValues` and
 * `buildCreateData` — and, just as deliberately, out of the linked-fixture resolution that
 * `EntityCrudFixtureManager` and `resolveDependencyOrder` drive off `isLinked`: a component is created from
 * its parent's form *after* the parent is saved, and its foreign key points back at that parent, so
 * pre-creating one would ask for a cycle. The relationship suite drives these testers instead.
 */
export abstract class RelationshipControlTester extends ControlTester {
  override readonly isInput = false;
  abstract readonly kind: RelationshipKind;
  /** `aria-label` of the per-row delete button, as authored on the row component. */
  abstract readonly rowDeleteAriaLabel: string;
  /** Whether deleting a row opens {@link DeleteConfirmationDialog} — true wherever a row destroys something. */
  readonly confirmsDelete: boolean = true;

  /** The row list; the fieldset around it is what {@link ControlTester.control} addresses. */
  innerLocator(): string {
    return 'ul';
  }

  /** Title of the control's add button, built as `'Add ' + linkedEntityName` by every one of the three. */
  addButtonName(): string {
    return `Add ${this.linkedEntityName() ?? ''}`;
  }

  /** No-op, and declared without parameters because there is nothing to read: a relationship has no value on
   * the form. The rows are asserted by `RelationshipFieldsetPO` instead. */
  override async assertValue(): Promise<void> {
    // intentionally empty
  }
}

/** Association: a row points at an entity that lives on its own, so removing it only detaches the reference. */
class RelatedEntitiesControlTester extends RelationshipControlTester {
  override readonly kind = 'RELATED_ENTITIES' as const;
  override readonly rowDeleteAriaLabel = 'Delete related entity reference';
  override readonly confirmsDelete = false;
}

/** Containment with the child in a table of its own: removing a row destroys the child. */
class ComponentsControlTester extends RelationshipControlTester {
  override readonly kind = 'COMPONENTS' as const;
  override readonly rowDeleteAriaLabel = 'Delete component';
}

/** Containment inside the owner's document: the row is edited on a route nested below the owner's. */
class EmbeddedComponentsControlTester extends RelationshipControlTester {
  override readonly kind = 'EMBEDDED_COMPONENTS' as const;
  override readonly rowDeleteAriaLabel = 'Delete embedded component';
}

/**
 * A single stored file attached to an entity: a fieldset holding at most one row, and the selector that puts
 * one there.
 *
 * `isInput` stays false for the same reason it does on the relationship controls, and with the same
 * consequence — the control is out of `fillForm`, `assertFieldValues` and `buildCreateData`. An artifact is not
 * a string the generated form data can carry: it is `{bucket, objectId, name, mimeType}`, and the object it
 * names lives in the object store rather than in the entity's own payload. Treating it as a value would also
 * make every CRUD test of every entity that has an artifact attribute upload a file, putting the object store
 * on the critical path of a suite that otherwise never touches it. The artifact suite drives this tester
 * instead.
 */
export class ArtifactControlTester extends ControlTester {
  override readonly isInput = false;
  /** `aria-label` of the row's delete button, as authored on `ArtifactComponent`. */
  readonly rowDeleteAriaLabel = 'Delete artifact reference';
  /** Label of the button that reveals the upload inputs — itself revealed only while the fieldset has focus. */
  readonly revealSelectorButtonName = 'Upload file';
  /** Label of the button that performs the upload, once the selector is open. */
  readonly uploadButtonName = 'Upload';

  /** The single-row list; the fieldset around it is what {@link ControlTester.control} addresses. */
  innerLocator(): string {
    return 'ul';
  }

  /** Whether the control renders a thumbnail rather than a MIME icon for `mimeType`. */
  showsThumbnailFor(mimeType: string): boolean {
    return this.showThumbnail() && mimeType.startsWith('image/');
  }

  /** `showThumbnail` is opt-out: `ArtifactComponent` suppresses the thumbnail only on an explicit `false`. */
  private showThumbnail(): boolean {
    return (this.attr as { showThumbnail?: boolean }).showThumbnail !== false;
  }

  /** No-op for the same reason the relationship testers' is: what the control holds is not a form value. */
  override async assertValue(): Promise<void> {
    // intentionally empty
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

  /** No-op: a control type the suites do not yet drive has nothing to assert. */
  override async assertValue(): Promise<void> {
    // intentionally empty
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
    case 'RELATED_ENTITIES':
      return new RelatedEntitiesControlTester(attr);
    case 'COMPONENTS':
      return new ComponentsControlTester(attr);
    case 'EMBEDDED_COMPONENTS':
      return new EmbeddedComponentsControlTester(attr);
    case 'ARTIFACT':
      return new ArtifactControlTester(attr);
    case 'FLEX_BOX':
    case 'LABEL':
      return new NoopControlTester(attr, false);
    default:
      return new NoopControlTester(attr, true);
  }
}

/** The attributes a test can act on at all: the invisible and the disabled ones offer no interaction. */
function testableAttrs(descriptor: BaseEntityDescriptor): BaseEntityAttrDescriptor[] {
  return (descriptor.attrDescriptors as BaseEntityAttrDescriptor[]).filter((attr) => attr.visible !== false && attr.disabled !== true);
}

export function controlTestersFor(descriptor: BaseEntityDescriptor): ControlTester[] {
  return testableAttrs(descriptor)
    .map((attr) => createControlTester(attr))
    .filter((tester) => tester.isInput);
}

/**
 * The to-many relationship controls of an entity. Complements {@link controlTestersFor}, which returns the
 * scalar inputs: the two sets are disjoint, and together they cover every attribute a test can act on.
 */
export function relationshipTestersFor(descriptor: BaseEntityDescriptor): RelationshipControlTester[] {
  return testableAttrs(descriptor)
    .map((attr) => createControlTester(attr))
    .filter((tester): tester is RelationshipControlTester => tester instanceof RelationshipControlTester);
}

/** The artifact attributes of an entity — like {@link relationshipTestersFor}, disjoint from the scalar inputs. */
export function artifactTestersFor(descriptor: BaseEntityDescriptor): ArtifactControlTester[] {
  return testableAttrs(descriptor)
    .map((attr) => createControlTester(attr))
    .filter((tester): tester is ArtifactControlTester => tester instanceof ArtifactControlTester);
}

/**
 * The attribute holding a non-embedded component's foreign key back to `ownerEntityName`.
 *
 * The plain-data twin of `BaseEntityDescriptor.parentReferenceAttrName()`: registry descriptors arrive as
 * JSON, so the class's own method is not available on them.
 */
export function parentReferenceAttrName(childDescriptor: BaseEntityDescriptor, ownerEntityName: string): string | undefined {
  return (childDescriptor.attrDescriptors as BaseEntityAttrDescriptor[]).find((attr) => (attr.formControlType as string) === 'FOREIGN_KEY' && attr.linkedEntityType === ownerEntityName)?.attrName;
}

export function identificationAttrFromTesters(descriptor: BaseEntityDescriptor): BaseEntityAttrDescriptor | undefined {
  return controlTestersFor(descriptor).find((tester) => tester.attr.isLinkToDetails === true)?.attr;
}

export function formControlLocatorForType(type: FormControlType): string {
  return createControlTester({ formControlType: type } as BaseEntityAttrDescriptor).innerLocator();
}
