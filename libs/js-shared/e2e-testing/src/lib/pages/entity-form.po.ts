import { expect, type Page } from '@playwright/test';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { buttonTestId, toTestId } from '../selectors/selector.builder';
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

export interface EntityFormPOOptions {
  /** Default expect timeout used when a method call does not provide one in context options. */
  expectTimeoutMs?: number;
}

type EntityFormPODependencyOptions = Map<string, BaseEntityDescriptor> | EntityFormPOOptions;

export class EntityFormPO {
  private readonly descriptorMap: Map<string, BaseEntityDescriptor>;
  private readonly options: EntityFormPOOptions;

  constructor(
    private page: Page,
    private descriptor: BaseEntityDescriptor,
    private routes: RouteResolver,
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
    await this.page.waitForURL(/\/list$/);
  }

  /**
   * Save on an embedded child's form, which returns to the form of the entity containing it rather than to a
   * list — an embedded child has no list of its own. The owner's URL has to be captured before the
   * drill-down: it ends in `/details` just as the child's does, so no pattern tells the two apart.
   */
  async saveReturningTo(returnUrl: string) {
    await this.clickSave();
    await this.page.waitForURL(returnUrl);
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

  private async clickSave() {
    if (this.descriptor.isAbstract) {
      throw new Error(`[${this.descriptor.entityName}] is abstract; form save is not applicable`);
    }

    await this.page.getByTestId(buttonTestId(this.descriptor.entityName, 'save')).click();
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
