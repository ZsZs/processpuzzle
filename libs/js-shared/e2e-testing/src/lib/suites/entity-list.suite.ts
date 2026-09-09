import { test } from '@playwright/test';
import * as fs from 'node:fs';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { EntityListPO } from '../pages/entity-list.po';
import { RouteResolver } from '../routing/route.resolver';
import type { ExcludedEntity } from './entity-crud.suite';

export interface DefineEntityListSuiteOptions {
  /** Absolute path to the registry JSON produced by globalSetup. */
  registryPath: string;
  /** Route prefix shared by every entity list/detail URL, e.g. '/base-entity/samples'. */
  routePrefix: string;
  /**
   * Entities whose list this suite is told not to exercise, and why.
   *
   * The test is still registered and skipped with the reason, so the gap stays visible in the report rather
   * than disappearing from it — the same arrangement `defineEntityCrudSuite` and
   * `defineEntityRelationshipSuite` use, and the reason this option exists: the assertion here is that a list
   * renders *rows*, which no entity can satisfy when nothing creates one. A read-only entity is the case in
   * point — a `Workflow Instance` exists only after `POST /instances`, and no seed file can start one.
   */
  excludedEntities?: ExcludedEntity[];
}



/**
 * Registers `[<entityName>] LIST` describe blocks for every entity in the registry.
 * Call once per spec file.
 */
export function defineEntityListSuite(options: DefineEntityListSuiteOptions): void {
  const registry: BaseEntityDescriptor[] = JSON.parse(fs.readFileSync(options.registryPath, 'utf-8'));
  const routes = new RouteResolver(options.routePrefix);

  for (const descriptor of registry) {
    // Embedded components have no list of their own — they are rows inside their parent's form.
    if (descriptor.isEmbedded) continue;

    const excluded = options.excludedEntities?.find((entry) => entry.entityName === descriptor.entityName);

    test.describe(`[${descriptor.entityName}] LIST`, () => {
      test('renders toolbar and rows', async ({ page }) => {
        test.skip(excluded !== undefined, `excluded by the application: ${excluded?.reason}`);

        const list = new EntityListPO(page, descriptor, routes);

        await list.navigateTo();
        await list.assertNewButtonVisible();
        await list.assertFilterVisible();
        await list.assertNotEmpty();

        if (descriptor.isAbstract) {
          await list.assertNewButtonDisabled();
          await list.selectFirstRow();
          await list.assertDeleteButtonDisabled();
        } else {
          await list.assertNewButtonEnabled();
        }
      });
    });
  }
}
