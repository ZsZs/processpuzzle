import type { Page } from '@playwright/test';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { buttonTestId } from '../selectors/selector.builder';
import {
  type ControlDataContext,
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
}

export class EntityFormPO {
  constructor(
    private page: Page,
    private descriptor: BaseEntityDescriptor,
    private routes: RouteResolver,
    private descriptorMap: Map<string, BaseEntityDescriptor> = new Map(),
  ) {}

  // Navigation

  async navigateToDetail(entityId: string) {
    await this.page.goto(this.routes.detailRoute(this.descriptor.entityName, entityId));
    await this.page.waitForURL(/\/details$/);
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

  private controlContext(
    linkedIdentifications: Record<string, string>,
    contextOptions: EntityFormContextOptions,
  ): ControlDataContext & { page: Page; routes: RouteResolver } {
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
    };
  }
}
