import { chromium, type FullConfig } from '@playwright/test';
import * as fs from 'node:fs';
import * as path from 'node:path';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { resolveDependencyOrder } from '../data/dependency.resolver';

export interface CreateGlobalSetupOptions {
  /** Absolute path where the resolved registry will be written. */
  registryPath: string;
  /** Route on the application that returns the JSON descriptor registry. Defaults to '/entity-registry'. */
  registryUrl?: string;
  /**
   * Base path per entity name, for an entity the application cannot report one for.
   *
   * The registry derives every route by walking the router's configuration, and a `loadChildren` branch that
   * has never been entered is not in it — the registry endpoint is a page load of its own, so no branch but
   * its own has been expanded. An entity mounted there is therefore serialized without a route, and the
   * suites would fall back to guessing `<routePrefix>/<kebab-name>`, which the router answers with NG04002.
   *
   * Naming the base path here is the application's own statement of where it mounted that branch —
   * `{ 'App Definition': '/design/app-definition' }` — and it is used only where the registry reported none,
   * so it goes stale loudly rather than silently overriding what the application knows.
   */
  routeOverrides?: Record<string, string>;
}

export function createGlobalSetup(options: CreateGlobalSetupOptions): (config: FullConfig) => Promise<void> {
  const { registryPath, registryUrl = '/entity-registry', routeOverrides = {} } = options;

  return async function globalSetup(config: FullConfig) {
    const baseURL = config.projects[0].use.baseURL;
    const browser = await chromium.launch();
    const page = await browser.newPage();

    console.log(`[global-setup] Navigating to entity registry at ${baseURL}${registryUrl}`);
    // `domcontentloaded` rather than the default `load`: the registry page's contract is the `<pre>` waited for
    // on the next line, and `load` additionally waits out everything the application shell starts on the side —
    // the Firestore listen stream, and the hidden session-status iframe the OIDC client mounts against the
    // identity provider. Neither has anything to do with the descriptors, and either can outlive the timeout on
    // a loaded machine, failing the whole run before a single test is registered.
    await page.goto(`${baseURL}${registryUrl}`, { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('pre');

    const json = await page.locator('pre').textContent();
    if (!json) throw new Error(`[global-setup] Empty response from ${baseURL}${registryUrl}`);
    const raw: BaseEntityDescriptor[] = JSON.parse(json);

    const ordered = resolveDependencyOrder(raw);
    for (const descriptor of ordered) descriptor.route ??= routeOverrides[descriptor.entityName];
    warnAboutUnroutedEntities(ordered);

    fs.mkdirSync(path.dirname(registryPath), { recursive: true });
    fs.writeFileSync(registryPath, JSON.stringify(ordered, null, 2));

    await browser.close();
  };
}

/**
 * An entity with no base path is one the generated suites cannot address: they fall back to
 * `<routePrefix>/<kebab-name>`, and where that is not where the application mounted it the router answers
 * NG04002 — a failure three layers away from its cause. Saying so here, next to the registry that reported it,
 * is what makes the cause findable.
 *
 * Embedded entities are the legitimate case: they have no route of their own by design, being addressed
 * relative to the entity whose payload carries them.
 */
function warnAboutUnroutedEntities(descriptors: BaseEntityDescriptor[]): void {
  const unrouted = descriptors.filter((descriptor) => !descriptor.route && !descriptor.isEmbedded).map((descriptor) => descriptor.entityName);
  if (unrouted.length === 0) return;

  console.warn(
    `[global-setup] The registry reports no route for: ${unrouted.join(', ')}. ` +
      `A lazily loaded route branch is the usual reason — name its base path in createGlobalSetup's routeOverrides. ` +
      `Until then the suites address these entities as '<routePrefix>/<kebab-name>'.`,
  );
}
