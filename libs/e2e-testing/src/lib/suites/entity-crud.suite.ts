import { test } from '@playwright/test';
import * as fs from 'fs';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { EntityFormPO } from '../pages/entity-form.po';
import { EntityListPO } from '../pages/entity-list.po';
import { RouteResolver } from '../routing/route.resolver';
import { buildCreateData, buildLinkedIdentifications, buildUpdateData, identificationAttr } from '../data/test-data-factory';

export interface DefineEntityCrudSuiteOptions {
  /** Absolute path to the registry JSON produced by globalSetup. */
  registryPath: string;
  /** Route prefix shared by every entity list/detail URL, e.g. '/base-entity/samples'. */
  routePrefix: string;
}

/**
 * Registers a CRUD suite that runs phase-by-phase across every entity in the registry:
 *   CREATE-all (registry order) → READ-all → UPDATE-all → DELETE-all (reverse order).
 *
 * Phase-major ordering lets linked entities (FOREIGN_KEY, LOOKUP) reference rows that
 * were created earlier in the same phase, and survive long enough to be referenced in
 * later phases. Reverse-order DELETE removes children before parents.
 */
export function defineEntityCrudSuite(options: DefineEntityCrudSuiteOptions): void {
  const registry: BaseEntityDescriptor[] = JSON.parse(fs.readFileSync(options.registryPath, 'utf-8'));
  const routes = new RouteResolver(options.routePrefix);
  const descriptorMap = new Map(registry.map((d) => [d.entityName, d]));

  test.describe.configure({ mode: 'serial' });

  // Shared maps: entityName → created entityId (UUID) / created data payload.
  const createdIds: Record<string, string> = {};
  const createdData: Record<string, Record<string, string>> = {};
  const runSuffix = `e2e-${Date.now().toString(36)}`;

  test.describe('CREATE', () => {
    for (const descriptor of registry) {
      const idAttr = identificationAttr(descriptor);

      test(`[${descriptor.entityName}] CREATE`, async ({ page }) => {
        const form = new EntityFormPO(page, descriptor, routes, descriptorMap);
        const list = new EntityListPO(page, descriptor, routes);
        const data = buildCreateData(descriptor, createdIds, runSuffix);
        const linkedIdentifications = buildLinkedIdentifications(descriptor, descriptorMap, createdData);

        if (!idAttr) {
          test.skip(true, 'no identification attr — cannot capture entity id');
          return;
        }

        await list.navigateTo();
        await list.clickNew();
        await form.fillForm(data, linkedIdentifications);
        await form.save();

        const idValue = data[idAttr.attrName];
        await list.assertInList(idValue);

        const entityId = await list.openDetailsByIdentification(idValue);
        createdIds[descriptor.entityName] = entityId;
        createdData[descriptor.entityName] = data;

        await form.assertFieldValues(data, linkedIdentifications);
      });
    }
  });

  test.describe('READ', () => {
    for (const descriptor of registry) {
      test(`[${descriptor.entityName}] READ`, async ({ page }) => {
        const entityId = createdIds[descriptor.entityName];
        if (!entityId) {
          test.skip(true, 'no entity id captured in CREATE phase');
          return;
        }

        const form = new EntityFormPO(page, descriptor, routes, descriptorMap);
        const data = createdData[descriptor.entityName] ?? buildCreateData(descriptor, createdIds, runSuffix);
        const linkedIdentifications = buildLinkedIdentifications(descriptor, descriptorMap, createdData);

        await form.navigateToDetail(entityId);
        await form.assertFieldValues(data, linkedIdentifications);
      });
    }
  });

  test.describe('UPDATE', () => {
    for (const descriptor of registry) {
      test(`[${descriptor.entityName}] UPDATE`, async ({ page }) => {
        const entityId = createdIds[descriptor.entityName];
        if (!entityId) {
          test.skip(true, 'no entity id captured in CREATE phase');
          return;
        }

        const form = new EntityFormPO(page, descriptor, routes, descriptorMap);
        const original = createdData[descriptor.entityName] ?? buildCreateData(descriptor, createdIds, runSuffix);
        const updated = buildUpdateData(descriptor, original);
        const linkedIdentifications = buildLinkedIdentifications(descriptor, descriptorMap, createdData);

        await form.navigateToDetail(entityId);
        await form.fillForm(updated, linkedIdentifications, { skipLinked: true });
        await form.save();

        await form.navigateToDetail(entityId);
        await form.assertFieldValues(updated, linkedIdentifications);
        createdData[descriptor.entityName] = updated;
      });
    }
  });

  // DELETE in reverse order so children (FK holders) drop before their parents.
  test.describe('DELETE', () => {
    for (const descriptor of [...registry].reverse()) {
      const idAttr = identificationAttr(descriptor);

      test(`[${descriptor.entityName}] DELETE`, async ({ page }) => {
        const entityId = createdIds[descriptor.entityName];
        if (!entityId) {
          test.skip(true, 'no entity id captured in CREATE phase');
          return;
        }

        const form = new EntityFormPO(page, descriptor, routes, descriptorMap);
        const list = new EntityListPO(page, descriptor, routes);
        const data = createdData[descriptor.entityName] ?? buildCreateData(descriptor, createdIds, runSuffix);

        await form.navigateToDetail(entityId);
        await form.delete();

        if (idAttr) {
          await list.assertNotInList(data[idAttr.attrName]);
        }
      });
    }
  });
}
