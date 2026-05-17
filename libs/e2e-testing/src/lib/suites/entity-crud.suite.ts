import { test } from '@playwright/test';
import * as fs from 'fs';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { EntityFormPO } from '../pages/entity-form.po';
import { EntityListPO } from '../pages/entity-list.po';
import { RouteResolver } from '../routing/route.resolver';
import { buildCreateData, buildUpdateData, identificationAttr } from '../data/test-data-factory';

export interface DefineEntityCrudSuiteOptions {
  /** Absolute path to the registry JSON produced by globalSetup. */
  registryPath: string;
  /** Route prefix shared by every entity list/detail URL, e.g. '/base-entity/samples'. */
  routePrefix: string;
}

/**
 * Registers `[<entityName>] CRUD` describe blocks for every entity in the registry.
 * Runs CREATE → READ → UPDATE → DELETE in serial mode, propagating created ids for FK resolution.
 * Call once per spec file.
 */
export function defineEntityCrudSuite(options: DefineEntityCrudSuiteOptions): void {
  const registry: BaseEntityDescriptor[] = JSON.parse(fs.readFileSync(options.registryPath, 'utf-8'));
  const routes = new RouteResolver(options.routePrefix);

  test.describe.configure({ mode: 'serial' });

  // Shared map: entityName → created entityId (for FK resolution)
  const createdIds: Record<string, string> = {};

  for (const descriptor of registry) {
    const idAttr = identificationAttr(descriptor);

    test.describe(`[${descriptor.entityName}] CRUD`, () => {
      test('CREATE', async ({ page }) => {
        const form = new EntityFormPO(page, descriptor, routes);
        const list = new EntityListPO(page, descriptor, routes);
        const data = buildCreateData(descriptor, createdIds);

        if (!idAttr) {
          test.skip(true, 'no identification attr — cannot capture entity id');
          return;
        }

        await list.navigateTo();
        await list.clickNew();
        await form.fillForm(data);
        await form.save();

        const idValue = data[idAttr.attrName];
        await list.assertInList(idValue);

        const entityId = await list.openDetailsByIdentification(idValue);
        createdIds[descriptor.entityName] = entityId;

        await form.assertFieldValues(data);
      });

      test('READ', async ({ page }) => {
        const entityId = createdIds[descriptor.entityName];
        const form = new EntityFormPO(page, descriptor, routes);
        const data = buildCreateData(descriptor, createdIds);

        await form.navigateToDetail(entityId);
        await form.assertFieldValues(data);
      });

      test('UPDATE', async ({ page }) => {
        const entityId = createdIds[descriptor.entityName];
        const form = new EntityFormPO(page, descriptor, routes);
        const original = buildCreateData(descriptor, createdIds);
        const updated = buildUpdateData(descriptor, original);

        await form.navigateToDetail(entityId);
        await form.fillForm(updated);
        await form.save();

        await form.navigateToDetail(entityId);
        await form.assertFieldValues(updated);
      });

      test('DELETE', async ({ page }) => {
        const entityId = createdIds[descriptor.entityName];
        const form = new EntityFormPO(page, descriptor, routes);
        const list = new EntityListPO(page, descriptor, routes);
        const original = buildCreateData(descriptor, createdIds);

        await form.navigateToDetail(entityId);
        await form.delete();

        if (idAttr) {
          await list.assertNotInList(original[idAttr.attrName]);
        }
      });
    });
  }
}
