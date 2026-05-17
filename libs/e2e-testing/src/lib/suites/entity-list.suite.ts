import { test } from '@playwright/test';
import * as fs from 'fs';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { EntityListPO } from '../pages/entity-list.po';
import { RouteResolver } from '../routing/route.resolver';

export interface DefineEntityListSuiteOptions {
  /** Absolute path to the registry JSON produced by globalSetup. */
  registryPath: string;
  /** Route prefix shared by every entity list/detail URL, e.g. '/base-entity/samples'. */
  routePrefix: string;
}

/**
 * Registers `[<entityName>] LIST` describe blocks for every entity in the registry.
 * Call once per spec file.
 */
export function defineEntityListSuite(options: DefineEntityListSuiteOptions): void {
  const registry: BaseEntityDescriptor[] = JSON.parse(fs.readFileSync(options.registryPath, 'utf-8'));
  const routes = new RouteResolver(options.routePrefix);

  for (const descriptor of registry) {
    test.describe(`[${descriptor.entityName}] LIST`, () => {
      test('renders toolbar and rows', async ({ page }) => {
        const list = new EntityListPO(page, descriptor, routes);

        await list.navigateTo();
        await list.assertNewButtonVisible();
        await list.assertFilterVisible();
        await list.assertNotEmpty();
      });
    });
  }
}
