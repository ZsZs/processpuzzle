import { test } from '@playwright/test';
import * as fs from 'fs';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { EntityCrudFixtureManager } from '../data/entity-crud-fixture-manager';
import { identificationAttr } from '../data/test-data-factory';
import { EntityFormPO } from '../pages/entity-form.po';
import { EntityListPO } from '../pages/entity-list.po';
import { RouteResolver } from '../routing/route.resolver';

export interface DefineEntityCrudSuiteOptions {
  /** Absolute path to the registry JSON produced by globalSetup. */
  registryPath: string;
  /** Route prefix shared by every entity list/detail URL, e.g. '/base-entity/samples'. */
  routePrefix: string;
}

/**
 * Registers independent CRUD tests for every entity in the registry.
 * Each test creates its own linked fixtures in setup and removes everything in teardown.
 */
export function defineEntityCrudSuite(options: DefineEntityCrudSuiteOptions): void {
  const registry: BaseEntityDescriptor[] = JSON.parse(fs.readFileSync(options.registryPath, 'utf-8'));
  const routes = new RouteResolver(options.routePrefix);
  const descriptorMap = new Map(registry.map((descriptor) => [descriptor.entityName, descriptor]));

  for (const descriptor of registry) {
    test.describe(`[${descriptor.entityName}] CRUD`, () => {
      test('CREATE', async ({ page }, testInfo) => {
        const manager = createFixtureManager(routes, descriptorMap, descriptor, testInfo.retry);

        try {
          await manager.testSetup(page, descriptor);
          await manager.createEntity(page, descriptor);
        } finally {
          await manager.testTearDown(page);
        }
      });

      test('READ', async ({ page }, testInfo) => {
        const manager = createFixtureManager(routes, descriptorMap, descriptor, testInfo.retry);

        try {
          await manager.testSetup(page, descriptor);
          const fixture = await manager.createEntity(page, descriptor);
          const form = new EntityFormPO(page, descriptor, routes, descriptorMap);

          await form.navigateToDetail(fixture.id);
          await form.assertFieldValues(fixture.data, manager.linkedIdentifications(descriptor), manager.contextOptions());
        } finally {
          await manager.testTearDown(page);
        }
      });

      test('UPDATE', async ({ page }, testInfo) => {
        const manager = createFixtureManager(routes, descriptorMap, descriptor, testInfo.retry);

        try {
          await manager.testSetup(page, descriptor);
          const fixture = await manager.createEntity(page, descriptor);
          const form = new EntityFormPO(page, descriptor, routes, descriptorMap);
          const updated = manager.buildUpdateData(descriptor, fixture.data);
          const linkedIdentifications = manager.linkedIdentifications(descriptor);

          await form.navigateToDetail(fixture.id);
          await form.fillForm(updated, linkedIdentifications, { skipLinked: true }, manager.contextOptions());
          await form.save();

          await form.navigateToDetail(fixture.id);
          await form.assertFieldValues(updated, linkedIdentifications, manager.contextOptions());
          fixture.data = updated;
        } finally {
          await manager.testTearDown(page);
        }
      });

      test('DELETE', async ({ page }, testInfo) => {
        const manager = createFixtureManager(routes, descriptorMap, descriptor, testInfo.retry);

        try {
          await manager.testSetup(page, descriptor);
          const fixture = await manager.createEntity(page, descriptor);
          const idAttr = identificationAttr(descriptor);
          const form = new EntityFormPO(page, descriptor, routes, descriptorMap);
          const list = new EntityListPO(page, descriptor, routes);

          await form.navigateToDetail(fixture.id);
          await form.delete();
          manager.markDeleted(fixture);

          if (idAttr) {
            await list.assertNotInList(fixture.data[idAttr.attrName]);
          }
        } finally {
          await manager.testTearDown(page);
        }
      });
    });
  }
}

function createFixtureManager(routes: RouteResolver, descriptorMap: Map<string, BaseEntityDescriptor>, descriptor: BaseEntityDescriptor, retry: number): EntityCrudFixtureManager {
  const suffix = `e2e-${Date.now().toString(36)}-${descriptor.entityName.replace(/\s+/g, '-').toLowerCase()}-r${retry}`;
  return new EntityCrudFixtureManager(routes, descriptorMap, suffix);
}
