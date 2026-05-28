import type { Page } from '@playwright/test';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import {
  type LinkedEntityFixture,
  controlTestersFor,
  linkedFixtureAttrKey,
} from '../controls/control-tester';
import { EntityFormPO, type EntityFormContextOptions } from '../pages/entity-form.po';
import { EntityListPO } from '../pages/entity-list.po';
import { RouteResolver } from '../routing/route.resolver';
import {
  buildCreateDataForContext,
  buildLinkedIdentificationsForContext,
  buildUpdateDataForContext,
  identificationAttr,
} from './test-data-factory';

export interface EntityFixture extends LinkedEntityFixture {
  descriptor: BaseEntityDescriptor;
  deleted: boolean;
}

export class EntityCrudFixtureManager {
  private readonly createdIdsByEntity: Record<string, string> = {};
  private readonly createdDataByEntity: Record<string, Record<string, string>> = {};
  private readonly linkedFixturesByAttr: Record<string, LinkedEntityFixture> = {};
  private readonly fixtures: EntityFixture[] = [];

  constructor(
    private readonly routes: RouteResolver,
    private readonly descriptorMap: Map<string, BaseEntityDescriptor>,
    private readonly uniqueSuffix: string,
    private readonly expectTimeoutMs?: number,
  ) {}

  async testSetup(page: Page, descriptor: BaseEntityDescriptor): Promise<void> {
    await this.ensureLinkedFixtures(page, descriptor, []);
  }

  async testTearDown(page: Page): Promise<void> {
    if (page.isClosed()) return;

    const errors: string[] = [];

    for (const fixture of [...this.fixtures].reverse()) {
      if (fixture.deleted) continue;

      try {
        await this.deleteFixture(page, fixture);
      } catch (error) {
        errors.push(`${fixture.entityName} ${fixture.id}: ${error instanceof Error ? error.message : String(error)}`);
      }
    }

    if (errors.length > 0) {
      throw new Error(`Failed to clean up ${errors.length} e2e fixture(s): ${errors.join('; ')}`);
    }
  }

  async createEntity(page: Page, descriptor: BaseEntityDescriptor, suffix: string = this.uniqueSuffix): Promise<EntityFixture> {
    await this.ensureLinkedFixtures(page, descriptor, []);

    const context = this.context(descriptor, suffix);
    const data = buildCreateDataForContext(context);
    const linkedIdentifications = buildLinkedIdentificationsForContext(context);
    const idAttr = identificationAttr(descriptor);

    if (!idAttr) {
      throw new Error(`[${descriptor.entityName}] no identification attr; cannot create an independent CRUD fixture`);
    }

    const form = new EntityFormPO(page, descriptor, this.routes, this.descriptorMap);
    const list = new EntityListPO(page, descriptor, this.routes);

    await list.navigateTo();
    await list.clickNew();
    await form.fillForm(data, linkedIdentifications, {}, this.contextOptions(suffix));
    await form.save();

    const displayValue = data[idAttr.attrName];
    const fixture: EntityFixture = {
      entityName: descriptor.entityName,
      descriptor,
      id: '',
      data,
      displayValue,
      deleted: false,
    };

    this.registerFixture(fixture);
    await list.assertInList(displayValue);

    fixture.id = await list.openDetailsByIdentification(displayValue);
    this.updateRegisteredFixture(fixture);
    await form.assertFieldValues(data, linkedIdentifications, this.contextOptions(suffix));

    return fixture;
  }

  buildUpdateData(descriptor: BaseEntityDescriptor, original: Record<string, string>): Record<string, string> {
    return buildUpdateDataForContext(this.context(descriptor), original);
  }

  linkedIdentifications(descriptor: BaseEntityDescriptor): Record<string, string> {
    return buildLinkedIdentificationsForContext(this.context(descriptor));
  }

  contextOptions(uniqueSuffix = this.uniqueSuffix): EntityFormContextOptions {
    return {
      createdIdsByEntity: this.createdIdsByEntity,
      createdDataByEntity: this.createdDataByEntity,
      linkedFixturesByAttr: this.linkedFixturesByAttr,
      uniqueSuffix,
      expectTimeoutMs: this.expectTimeoutMs,
    };
  }

  markDeleted(fixture: EntityFixture): void {
    fixture.deleted = true;
  }

  private async ensureLinkedFixtures(page: Page, descriptor: BaseEntityDescriptor, stack: string[]): Promise<void> {
    if (stack.includes(descriptor.entityName)) {
      throw new Error(`Linked entity dependency cycle detected: ${[...stack, descriptor.entityName].join(' -> ')}`);
    }

    const nextStack = [...stack, descriptor.entityName];
    for (const tester of controlTestersFor(descriptor)) {
      if (!tester.isLinked) continue;

      const linkedName = tester.linkedEntityName();
      if (!linkedName) continue;

      const key = linkedFixtureAttrKey(descriptor.entityName, tester.attr.attrName);
      if (this.linkedFixturesByAttr[key]) continue;

      const linkedDescriptor = this.descriptorMap.get(linkedName);
      if (!linkedDescriptor) {
        throw new Error(`[${descriptor.entityName}.${tester.attr.attrName}] linked entity '${linkedName}' is not present in the registry`);
      }

      const fixture = await this.createLinkedEntity(page, linkedDescriptor, `${this.uniqueSuffix}-${tester.attr.attrName}`, nextStack);
      this.linkedFixturesByAttr[key] = fixture;
    }
  }

  private async createLinkedEntity(page: Page, descriptor: BaseEntityDescriptor, suffix: string, stack: string[]): Promise<EntityFixture> {
    await this.ensureLinkedFixtures(page, descriptor, stack);
    return this.createEntityWithoutCycleCheck(page, descriptor, suffix);
  }

  private async createEntityWithoutCycleCheck(page: Page, descriptor: BaseEntityDescriptor, suffix: string): Promise<EntityFixture> {
    const context = this.context(descriptor, suffix);
    const data = buildCreateDataForContext(context);
    const linkedIdentifications = buildLinkedIdentificationsForContext(context);
    const idAttr = identificationAttr(descriptor);

    if (!idAttr) {
      throw new Error(`[${descriptor.entityName}] no identification attr; cannot create linked fixture`);
    }

    const form = new EntityFormPO(page, descriptor, this.routes, this.descriptorMap);
    const list = new EntityListPO(page, descriptor, this.routes);

    await list.navigateTo();
    await list.clickNew();
    await form.fillForm(data, linkedIdentifications, {}, this.contextOptions(suffix));
    await form.save();

    const displayValue = data[idAttr.attrName];
    const fixture: EntityFixture = {
      entityName: descriptor.entityName,
      descriptor,
      id: '',
      data,
      displayValue,
      deleted: false,
    };

    this.registerFixture(fixture);
    await list.assertInList(displayValue);

    fixture.id = await list.openDetailsByIdentification(displayValue);
    this.updateRegisteredFixture(fixture);
    return fixture;
  }

  private async deleteFixture(page: Page, fixture: EntityFixture): Promise<void> {
    const list = new EntityListPO(page, fixture.descriptor, this.routes);

    if (!fixture.id) {
      await list.deleteByIdentification(fixture.displayValue);
      fixture.deleted = true;
      return;
    }

    const form = new EntityFormPO(page, fixture.descriptor, this.routes, this.descriptorMap);
    try {
      await form.navigateToDetail(fixture.id);
      await form.delete();
    } catch {
      await list.deleteByIdentification(fixture.displayValue);
    }
    fixture.deleted = true;
  }

  private registerFixture(fixture: EntityFixture): void {
    this.fixtures.push(fixture);
    this.updateRegisteredFixture(fixture);
  }

  private updateRegisteredFixture(fixture: EntityFixture): void {
    this.createdIdsByEntity[fixture.entityName] = fixture.id;
    this.createdDataByEntity[fixture.entityName] = fixture.data;
  }

  private context(descriptor: BaseEntityDescriptor, uniqueSuffix = this.uniqueSuffix) {
    return {
      descriptor,
      descriptorMap: this.descriptorMap,
      createdIdsByEntity: this.createdIdsByEntity,
      createdDataByEntity: this.createdDataByEntity,
      linkedFixturesByAttr: this.linkedFixturesByAttr,
      uniqueSuffix,
    };
  }
}
