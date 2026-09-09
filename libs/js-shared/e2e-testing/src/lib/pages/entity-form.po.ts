import { expect, type Page } from '@playwright/test';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { blockingViolationsSelector, buttonTestId, toTestId } from '../selectors/selector.builder';
import {
  type ControlInteractionContext,
  type FillControlOptions,
  type LinkedEntityFixture,
  controlTestersFor,
} from '../controls/control-tester';
import { RouteResolver } from '../routing/route.resolver';

export type FillFormOptions = FillControlOptions;

export interface EntityFormContextOptions {
  createdIdsByEntity?: Record<string, string>;
  createdDataByEntity?: Record<string, Record<string, string>>;
  linkedFixturesByAttr?: Record<string, LinkedEntityFixture>;
  uniqueSuffix?: string;
  /** Overrides Playwright's default expect timeout for form control interactions/assertions. */
  expectTimeoutMs?: number;
}

/** Why a form is still open: it was left, the wait for that failed, or the rule engine rejected the write. */
interface LeaveFormOutcome {
  left: boolean;
  error?: Error;
}

/** How long a save is given to navigate once ERROR verdicts are on screen. See `leaveFormAfterSave`. */
const BLOCKED_SAVE_GRACE_MS = 3_000;

/** How long Save is given to enable before the form's state is reported instead. See `clickSave`. */
const SAVE_ENABLED_TIMEOUT_MS = 15_000;

export interface EntityFormPOOptions {
  /** Default expect timeout used when a method call does not provide one in context options. */
  expectTimeoutMs?: number;
}

type EntityFormPODependencyOptions = Map<string, BaseEntityDescriptor> | EntityFormPOOptions;

export class EntityFormPO {
  private readonly descriptorMap: Map<string, BaseEntityDescriptor>;
  private readonly options: EntityFormPOOptions;

  constructor(
    private readonly page: Page,
    private readonly descriptor: BaseEntityDescriptor,
    private readonly routes: RouteResolver,
    descriptorMapOrOptions: EntityFormPODependencyOptions = new Map(),
    options: EntityFormPOOptions = {},
  ) {
    this.descriptorMap = descriptorMapOrOptions instanceof Map ? descriptorMapOrOptions : new Map();
    this.options = descriptorMapOrOptions instanceof Map ? options : descriptorMapOrOptions;
  }

  // Navigation

  async navigateToDetail(entityId: string) {
    await this.page.goto(this.routes.detailRoute(this.descriptor, entityId));
    await this.page.waitForURL(/\/details$/);
  }

  /** The URL of the form currently open — the anchor an embedded drill-down returns to. */
  currentUrl(): string {
    return this.page.url();
  }

  // Form interactions

  async fillForm(
    data: Record<string, string>,
    linkedIdentifications: Record<string, string> = {},
    options: FillFormOptions = {},
    contextOptions: EntityFormContextOptions = {},
  ) {
    const context = this.controlContext(linkedIdentifications, contextOptions);
    const testers = [...controlTestersFor(this.descriptor)].sort((a, b) => a.fillOrder - b.fillOrder);

    for (const tester of testers) {
      const value = data[tester.attr.attrName] ?? '';
      await tester.fill(context, value, options);
    }
  }

  /** Save navigates back to the list. */
  async save() {
    await this.clickSave();
    await this.leaveFormAfterSave(/\/list$/);
  }

  /**
   * Save on an embedded child's form, which returns to the form of the entity containing it rather than to a
   * list — an embedded child has no list of its own. The owner's URL has to be captured before the
   * drill-down: it ends in `/details` just as the child's does, so no pattern tells the two apart.
   */
  async saveReturningTo(returnUrl: string) {
    await this.clickSave();
    await this.leaveFormAfterSave(returnUrl);
  }

  /** Delete on an embedded child's form; returns to the containing entity's form. See {@link saveReturningTo}. */
  async deleteReturningTo(returnUrl: string) {
    await this.clickDelete();
    await this.page.waitForURL(returnUrl);
  }

  async edit() {
    if (this.descriptor.isAbstract) {
      throw new Error(`[${this.descriptor.entityName}] is abstract; form edit is not applicable`);
    }

    await this.page.getByTestId(buttonTestId(this.descriptor.entityName, 'edit')).click();
  }

  /** Delete navigates back to the list. */
  async delete() {
    await this.clickDelete();
    await this.page.waitForURL(/\/list$/);
  }

  // Assertions

  async assertFieldValues(
    data: Record<string, string>,
    linkedIdentifications: Record<string, string> = {},
    contextOptions: EntityFormContextOptions = {},
  ) {
    const context = this.controlContext(linkedIdentifications, contextOptions);
    for (const tester of controlTestersFor(this.descriptor)) {
      const value = data[tester.attr.attrName] ?? '';
      await tester.assertValue(context, value);
    }
  }

  /**
   * The status bar's breadcrumb, outermost entity first. On an embedded child's form it is the only thing
   * naming the chain of entities the row is reached through, the child's own screens carrying no list.
   */
  async assertBreadcrumb(entityNames: string[], contextOptions: EntityFormContextOptions = {}) {
    const timeout = contextOptions.expectTimeoutMs ?? this.options.expectTimeoutMs;
    const expectOptions = timeout === undefined ? undefined : { timeout };

    for (const entityName of entityNames) {
      await expect(this.page.getByTestId(`${toTestId(entityName)}-breadcrumb`)).toBeVisible(expectOptions);
    }
  }

  /**
   * Waits for a save to leave the form, and gives up as soon as it is clear that it will not.
   *
   * A save the rule engine rejects is a no-op: the form stays put and renders the ERROR verdicts instead.
   * Waiting for the navigation alone would burn the whole test budget and then report the timeout against
   * whatever call happened to be in flight, which says nothing about the rule that blocked the write.
   *
   * Only saves are guarded. A delete is not rule-checked, so verdicts on screen say nothing about whether it
   * will go through.
   */
  private async leaveFormAfterSave(urlPattern: string | RegExp) {
    const blockingViolations = this.page.locator(blockingViolationsSelector());
    // Neither branch may reject: the losing one is never awaited, so its rejection would surface as an
    // unhandled one once the race has already been decided. The navigation's own failure is carried as a
    // value and rethrown below instead.
    const navigated = this.page.waitForURL(urlPattern).then<LeaveFormOutcome, LeaveFormOutcome>(
      () => ({ left: true }),
      (error: Error) => ({ left: false, error }),
    );
    const violated = blockingViolations
      .first()
      .waitFor({ state: 'visible' })
      .then<LeaveFormOutcome, LeaveFormOutcome>(
        () => ({ left: false }),
        () => new Promise<LeaveFormOutcome>(() => undefined),
      );

    let outcome = await Promise.race([navigated, violated]);
    if (!outcome.left && !outcome.error) {
      // Verdicts a field-level evaluation left on screen, which the rest of the fill has since satisfied,
      // are still rendered for the moment it takes the submit to re-evaluate them. The write counts as
      // blocked only if they are still there once the navigation has had its chance.
      const grace = this.page.waitForTimeout(BLOCKED_SAVE_GRACE_MS).then((): LeaveFormOutcome | undefined => undefined);
      outcome = (await Promise.race([navigated, grace])) ?? outcome;
    }
    if (outcome.left) return;
    if (outcome.error) throw outcome.error;

    const messages = (await blockingViolations.allTextContents()).map((message) => message.trim()).filter(Boolean);
    if (messages.length === 0) {
      // Cleared after all, and the navigation is merely slow: back to waiting for it.
      const late = await navigated;
      if (late.error) throw late.error;
      return;
    }

    throw new Error(`[${this.descriptor.entityName}] the form was not left: the save is blocked by ERROR rule violation(s): ${messages.join('; ')}`);
  }

  private async clickSave() {
    if (this.descriptor.isAbstract) {
      throw new Error(`[${this.descriptor.entityName}] is abstract; form save is not applicable`);
    }

    // Save stays disabled while the form is pristine or invalid, and a plain click would sit out the whole
    // test budget waiting for it — reporting a timeout on the click rather than the state that caused it.
    const saveButton = this.page.getByTestId(buttonTestId(this.descriptor.entityName, 'save'));
    try {
      await expect(saveButton).toBeEnabled({ timeout: SAVE_ENABLED_TIMEOUT_MS });
    } catch {
      throw new Error(`[${this.descriptor.entityName}] Save stayed disabled: the form is pristine, invalid, or holding an ERROR rule violation`);
    }

    await saveButton.click();
  }

  private async clickDelete() {
    if (this.descriptor.isAbstract) {
      throw new Error(`[${this.descriptor.entityName}] is abstract; form delete is not applicable`);
    }

    await this.page.getByTestId(buttonTestId(this.descriptor.entityName, 'delete')).click();
  }

  private controlContext(
    linkedIdentifications: Record<string, string>,
    contextOptions: EntityFormContextOptions,
  ): ControlInteractionContext {
    return {
      page: this.page,
      routes: this.routes,
      descriptor: this.descriptor,
      descriptorMap: this.descriptorMap,
      createdIdsByEntity: contextOptions.createdIdsByEntity ?? {},
      createdDataByEntity: contextOptions.createdDataByEntity ?? {},
      linkedFixturesByAttr: contextOptions.linkedFixturesByAttr,
      linkedDisplayValuesByAttr: linkedIdentifications,
      uniqueSuffix: contextOptions.uniqueSuffix,
      expectTimeoutMs: contextOptions.expectTimeoutMs ?? this.options.expectTimeoutMs,
    };
  }
}
