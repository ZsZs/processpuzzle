import { chromium, expect, type FullConfig, type Page } from '@playwright/test';
import * as fs from 'node:fs';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { identificationAttr } from '../data/test-data-factory';
import { EntityListPO } from '../pages/entity-list.po';
import { RouteResolver } from '../routing/route.resolver';
import { buttonTestId } from '../selectors/selector.builder';

export interface CreateGlobalTeardownOptions {
  /** The registry the global setup wrote. */
  registryPath: string;
  /** The route prefix the generated suites use; see {@link RouteResolver}. */
  routePrefix: string;
}

/** What the generated suites put in every identification they invent: `e2e-<time>-<entity>-r<retry>`. */
const FIXTURE_MARK = /e2e-[a-z0-9]+-[a-z0-9-]+-r\d+/;

const MAX_ROWS_PER_ENTITY = 50;

/**
 * Removes the fixtures a run left behind.
 *
 * A test cleans up after itself, but a cleanup that did not run — the browser crashed, the worker was killed,
 * the deployment went away halfway — leaves its data on the stage for good, and a stage nobody tidies fills up
 * with it. This is the safety net: after the whole run, every entity's list is searched for the mark the suites
 * put in their identifications, and what is found is deleted.
 *
 * It never fails the run. Reporting what a test did is the tests' job; this only tidies, and says what it could
 * not.
 */
export function createGlobalTeardown(options: CreateGlobalTeardownOptions): (config: FullConfig) => Promise<void> {
  return async function globalTeardown(config: FullConfig) {
    const baseURL = config.projects[0].use.baseURL;
    if (!fs.existsSync(options.registryPath)) return;

    const registry: BaseEntityDescriptor[] = JSON.parse(fs.readFileSync(options.registryPath, 'utf-8'));
    const routes = new RouteResolver(options.routePrefix);
    const browser = await chromium.launch();
    const page = await (await browser.newContext({ baseURL })).newPage();
    page.setDefaultTimeout(15_000);

    try {
      // Dependants first: the generated order puts what an entity refers to before it.
      for (const descriptor of [...registry].reverse()) {
        if (descriptor.isAbstract || descriptor.isEmbedded || !descriptor.route || !identificationAttr(descriptor)) continue;
        await sweepEntity(page, new EntityListPO(page, descriptor, routes), descriptor);
      }
    } finally {
      await browser.close();
    }
  };
}

async function sweepEntity(page: Page, list: EntityListPO, descriptor: BaseEntityDescriptor): Promise<void> {
  let removed = 0;
  try {
    await list.navigateTo();
    await list.filter('e2e-');

    while (removed < MAX_ROWS_PER_ENTITY) {
      const row = list.rows().filter({ hasText: FIXTURE_MARK }).first();
      if (!(await row.isVisible({ timeout: 2_000 }).catch(() => false))) break;

      await row.locator('mat-checkbox input[type="checkbox"]').first().check();
      await page.getByTestId(buttonTestId(descriptor.entityName, 'delete')).click();
      await expect(row).toBeHidden();
      removed++;
    }
    if (removed > 0) console.warn(`[global-teardown] ${descriptor.entityName}: removed ${removed} leftover fixture(s).`);
  } catch (error) {
    console.warn(`[global-teardown] ${descriptor.entityName}: sweep stopped after ${removed} — ${error instanceof Error ? error.message.split('\n')[0] : String(error)}`);
  }
}
