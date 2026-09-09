import { expect, type Locator, type Page } from '@playwright/test';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import type { RelationshipControlTester } from '../controls/control-tester';
import { formControlTestId } from '../selectors/test-id';
import { exactText } from '../selectors/text-match';

export interface RelationshipFieldsetPOOptions {
  /** Overrides Playwright's default expect timeout — rows arrive through a store reload after a save. */
  expectTimeoutMs?: number;
}

/**
 * The `fieldset` one to-many relationship attribute renders: a row per reference, an add button, and a
 * delete button per row.
 *
 * The three relationship controls share this markup — `<ul>` of `<li>`, each row an `<a>` plus an icon
 * button — and differ only in what the buttons do, which is what the {@link RelationshipControlTester}
 * handed in describes. So one page object serves all three, and the suite's assertions are what tell
 * detaching from destroying.
 */
export class RelationshipFieldsetPO {
  constructor(
    private readonly page: Page,
    private readonly ownerDescriptor: BaseEntityDescriptor,
    private readonly tester: RelationshipControlTester,
    private readonly options: RelationshipFieldsetPOOptions = {},
  ) {}

  // ── Locators ────────────────────────────────────────────────────

  /**
   * Every form control host binds `data-testid` to `<entityName>-<attrName>`
   * (`BaseFormControlComponent.testId`), these fieldsets included.
   */
  fieldset(): Locator {
    return this.page.getByTestId(formControlTestId(this.ownerDescriptor.entityName, this.tester.attr.attrName));
  }

  rows(): Locator {
    return this.fieldset().locator('li');
  }

  /**
   * The `<fieldset>` inside the control's host — the host is what carries the test id, and this is what
   * carries `tabindex`, so it is the element focus has to land on.
   */
  private focusTarget(): Locator {
    return this.fieldset().locator('fieldset.base-entity-form-field').first();
  }

  /**
   * Focuses the fieldset, which is what reveals the add button.
   *
   * The button — and the hint standing in for it while the owner is unsaved — are `display: none` until
   * `.base-entity-form-field:focus-within` matches. A hidden element is not in the accessibility tree, so
   * without this a role query does not merely find it invisible: it does not find it at all.
   */
  async focusFieldset(): Promise<void> {
    await expect(this.focusTarget()).toBeVisible(this.expectOptions());
    await this.focusTarget().focus();
  }

  /** The hint the control shows in place of the add button while the owner has no document to store a row in. */
  private saveOwnerFirstHint(): Locator {
    return this.fieldset().locator('p.base-entity-form-hint');
  }

  /** The row whose link text is exactly `name` — a row's text is the child's identification value. */
  row(name: string): Locator {
    return this.rows()
      .filter({ has: this.page.locator('a').filter({ hasText: exactText(name) }) })
      .first();
  }

  addButton(): Locator {
    return this.fieldset().getByRole('button', { name: this.tester.addButtonName() });
  }

  // ── Actions ─────────────────────────────────────────────────────

  /** Enters the child's screens: a select-mode list for a reference, the child's own form for an embedded row. */
  async clickAdd(): Promise<void> {
    await this.focusFieldset();
    const addButton = this.addButton();
    await expect(addButton).toBeVisible(this.expectOptions());
    await addButton.click();
  }

  async openRow(name: string): Promise<void> {
    await this.row(name).locator('a').first().click();
  }

  /** Removes a row, confirming when the control's delete destroys something rather than detaching it. */
  async removeRow(name: string): Promise<void> {
    const deleteButton = this.row(name).getByRole('button', { name: this.tester.rowDeleteAriaLabel }).first();
    if (!this.tester.confirmsDelete) {
      await deleteButton.click();
      return;
    }

    // The click is retried until the dialog is up, because the row it lands on may not be the row that is
    // there a frame later: an embedded list re-renders when its store finishes reloading after a navigation,
    // and a click dispatched on the node being replaced reaches no handler. Waiting for the dialog instead of
    // for the click is what tells the two apart.
    const confirmButton = this.page.getByTestId('delete-confirmation-confirm');
    await expect(async () => {
      await deleteButton.click();
      await expect(confirmButton).toBeVisible({ timeout: 2000 });
    }).toPass({ timeout: this.options.expectTimeoutMs ?? 15000 });

    await confirmButton.click();
    await expect(confirmButton).toBeHidden(this.expectOptions());
  }

  // ── Assertions ──────────────────────────────────────────────────

  async assertRow(name: string): Promise<void> {
    await expect(this.row(name)).toBeVisible(this.expectOptions());
  }

  async assertNoRow(name: string): Promise<void> {
    await expect(this.row(name)).toHaveCount(0, this.expectOptions());
  }

  async assertRowCount(count: number): Promise<void> {
    await expect(this.rows()).toHaveCount(count, this.expectOptions());
  }

  async assertAddButtonVisible(): Promise<void> {
    await this.focusFieldset();
    await expect(this.addButton()).toBeVisible(this.expectOptions());
  }

  /**
   * An embedded row has nowhere to be stored until its owner exists, so the owner's new form offers no add.
   *
   * Focusing first is what makes this an assertion rather than a tautology: unfocused, the button is hidden on
   * every form. With focus, the control shows the hint instead — which is what is asserted here as well, so a
   * fieldset that revealed nothing at all could not pass.
   */
  async assertAddButtonHidden(): Promise<void> {
    await this.focusFieldset();
    await expect(this.saveOwnerFirstHint()).toBeVisible(this.expectOptions());
    await expect(this.addButton()).toHaveCount(0, this.expectOptions());
  }

  private expectOptions(): { timeout?: number } | undefined {
    return this.options.expectTimeoutMs === undefined ? undefined : { timeout: this.options.expectTimeoutMs };
  }
}
