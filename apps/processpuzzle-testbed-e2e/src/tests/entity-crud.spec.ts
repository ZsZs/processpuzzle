// src/tests/entity-crud.spec.ts
import { test } from '@playwright/test';
import * as fs from 'fs';
import { REGISTRY_PATH } from '../support/global-setup';
import { EntityDescriptor } from '../types/entity-descriptor.types';
import { EntityFormPO } from '../pages/entity-form.po';
import { EntityListPO } from '../pages/entity-list.po';
import { buildCreateData, buildUpdateData, identificationAttr } from '../support/test-data-factory';

test.describe.configure({ mode: 'serial' });

// Read registry written by globalSetup — already dependency-ordered
const registry: EntityDescriptor[] = JSON.parse(fs.readFileSync(REGISTRY_PATH, 'utf-8'));

// Shared map: entityName → created entityId (for FK resolution)
const createdIds: Record<string, string> = {};

for (const descriptor of registry) {
  const idAttr = identificationAttr(descriptor);

  test.describe(`[${descriptor.entityName}] CRUD`, () => {
    test('CREATE', async ({ page }) => {
      const form = new EntityFormPO(page, descriptor);
      const list = new EntityListPO(page, descriptor);
      const data = buildCreateData(descriptor, createdIds);

      if (!idAttr) {
        test.skip(true, 'no identification attr — cannot capture entity id');
        return;
      }

      await list.navigateTo();
      await list.clickNew();
      await form.fillForm(data);
      await form.save(); // navigates back to list

      // verify the new entity appears in the list, then click through to capture its id
      const idValue = data[idAttr.attrName];
      await list.assertInList(idValue);

      const entityId = await list.openDetailsByIdentification(idValue);
      createdIds[descriptor.entityName] = entityId;

      await form.assertFieldValues(data);
    });

    test('READ', async ({ page }) => {
      const entityId = createdIds[descriptor.entityName];
      const form = new EntityFormPO(page, descriptor);
      const data = buildCreateData(descriptor, createdIds);

      await form.navigateToDetail(entityId);
      await form.assertFieldValues(data);
    });

    test('UPDATE', async ({ page }) => {
      const entityId = createdIds[descriptor.entityName];
      const form = new EntityFormPO(page, descriptor);
      const original = buildCreateData(descriptor, createdIds);
      const updated = buildUpdateData(descriptor, original);

      await form.navigateToDetail(entityId);
      //      await form.edit();
      await form.fillForm(updated);
      await form.save(); // navigates back to list

      // re-open the details page to verify the saved values
      await form.navigateToDetail(entityId);
      await form.assertFieldValues(updated);
    });

    test('DELETE', async ({ page }) => {
      const entityId = createdIds[descriptor.entityName];
      const form = new EntityFormPO(page, descriptor);
      const list = new EntityListPO(page, descriptor);
      const original = buildCreateData(descriptor, createdIds);

      await form.navigateToDetail(entityId);
      await form.delete(); // navigates back to list

      if (idAttr) {
        await list.assertNotInList(original[idAttr.attrName]);
      }
    });
  });
}
