// src/tests/entity-list.spec.ts
import { test } from '@playwright/test';
import * as fs from 'fs';
import { REGISTRY_PATH } from '../support/global-setup';
import { EntityDescriptor } from '../types/entity-descriptor.types';
import { EntityListPO } from '../pages/entity-list.po';

// Assumes seeded test data — the list is never empty.
const registry: EntityDescriptor[] = JSON.parse(fs.readFileSync(REGISTRY_PATH, 'utf-8'));

for (const descriptor of registry) {
  test.describe(`[${descriptor.entityName}] LIST`, () => {
    test('renders toolbar and rows', async ({ page }) => {
      const list = new EntityListPO(page, descriptor);

      await list.navigateTo();
      await list.assertNewButtonVisible();
      await list.assertFilterVisible();
      await list.assertNotEmpty();
    });
  });
}
