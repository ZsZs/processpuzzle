import { expect, test as setup } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { mkdirSync } from 'node:fs';
import path from 'node:path';
import { serviceRoots } from './backend';
import { STORAGE_STATE } from './paths';

/** How long the branded form gets to take over from the plain one before the plain one is used. */
const BRANDED_FORM_TIMEOUT_MS = 15_000;

/**
 * Registers the presenter and signs in once, outside every recording: no tutorial shows a login.
 *
 * The same route as the e2e suite's `auth.setup.ts` — the testbed realm has open sign-up, so no seeded account and
 * no credential is needed, and the Keycloak session cookies kept in `storageState` are what keycloak-js restores
 * the session from. Unlike there, the account's name is one a viewer may read in the header.
 */
setup('register and sign in the presenter', async ({ page, request, baseURL }) => {
  setup.setTimeout(120_000);
  const roots = await serviceRoots(request, baseURL as string);
  const run = randomUUID().slice(0, 8);
  const presenter = { email: `tutorial-${run}@processpuzzle.local`, password: `Tutorial-${randomUUID()}`, firstName: 'Alex', lastName: 'Taylor' };

  let registration = await request.post(`${roots.objectStore}/registration`, { data: presenter });
  // Registration is rate limited per client address; the refusal says how long to wait.
  for (let attempt = 0; registration.status() === 429 && attempt < 2; attempt++) {
    await page.waitForTimeout(Math.min(Number(registration.headers()['retry-after']) || 30, 45) * 1000);
    registration = await request.post(`${roots.objectStore}/registration`, { data: presenter });
  }
  expect(registration.status(), `registering ${presenter.email}: ${await registration.text()}`).toBe(201);

  await page.goto('/auth/login');
  // Keycloak's page carries the branded `pp-login-form` element and a plain fallback that is hidden once the element
  // is defined; only what is visible after that has settled is used. See the e2e suite's `login-page.ts`.
  await page.locator('#username, #pp-fallback-username').first().waitFor({ state: 'attached' });
  await page.evaluate(
    (timeout) => Promise.race([customElements.whenDefined('pp-login-form'), new Promise((resolve) => setTimeout(resolve, timeout))]),
    BRANDED_FORM_TIMEOUT_MS,
  );
  await page.locator('#username, #pp-fallback-username').filter({ visible: true }).first().fill(presenter.email);
  await page.locator('#password, #pp-fallback-password').filter({ visible: true }).first().fill(presenter.password);
  await page.locator('form [type=submit]').filter({ visible: true }).first().click();

  await page.waitForURL((url) => url.origin === new URL(baseURL as string).origin);
  await expect(page.locator('pp-auth-button .avatar')).toBeVisible({ timeout: 30_000 });

  mkdirSync(path.dirname(STORAGE_STATE), { recursive: true });
  await page.context().storageState({ path: STORAGE_STATE });
});
