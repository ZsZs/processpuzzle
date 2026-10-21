import { expect, test as setup } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { AUTH_DIRECTORY, E2eUser, STORAGE_STATE, USER_FILE } from './auth.paths';
import { serviceRoots } from './race-sample.api';

/**
 * Registers a fresh user and signs in once, so that every `@auth` spec starts logged in.
 *
 * The testbed realm has open sign-up without email verification, which the backend exposes as
 * `POST /registration`, so no seeded account (and no credential) is needed — on ci and stage alike. The user
 * is unique per run; the testbed is wiped nightly, so nothing removes it afterwards.
 *
 * Sign-in is the real one: Keycloak does not allow the password grant for this client, so the setup drives the
 * ProcessPuzzle login page and keeps the Keycloak session cookies in `storageState`. keycloak-js restores the
 * session from them (`check-sso`) in every test that uses it.
 */
setup('register and sign in a test user', async ({ page, request, baseURL }) => {
  // A page load can take a while on a stack that has just started (a development server compiles on demand), and the
  // redirect to the identity provider and back loads the application twice.
  setup.setTimeout(120_000);
  const roots = await serviceRoots(request, baseURL as string);
  const run = randomUUID().slice(0, 8);
  const user = { email: `e2e-${run}@processpuzzle.local`, password: `E2e-${randomUUID()}`, firstName: 'E2E', lastName: `Tester ${run}` };

  let registration = await request.post(`${roots.objectStore}/registration`, { data: user });
  // Registration is rate limited per client address, and a developer who runs the suite again and again soon meets
  // the limit. The refusal says how long to wait (`Retry-After`), so wait that long, a little, rather than fail.
  for (let attempt = 0; registration.status() === 429 && attempt < 2; attempt++) {
    const seconds = Math.min(Number(registration.headers()['retry-after']) || 30, 45);
    await page.waitForTimeout(seconds * 1000);
    registration = await request.post(`${roots.objectStore}/registration`, { data: user });
  }
  expect(registration.status(), `registering ${user.email}: ${await registration.text()}`).toBe(201);

  await page.goto('/auth/login');
  // The branded sign-in is an Angular Element; its plain fallback form shows until that bundle has registered.
  await page.locator('#username, #pp-fallback-username').first().fill(user.email);
  await page.locator('#password, #pp-fallback-password').first().fill(user.password);
  await page.locator('form [type=submit]').first().click();

  await page.waitForURL((url) => url.origin === new URL(baseURL as string).origin);
  // The application boots again after the redirect (a development server compiles on the first request).
  await expect(page.getByRole('button', { name: 'Auth Button' })).toBeVisible({ timeout: 30_000 });
  await expect(page.locator('pp-auth-button .avatar')).toBeVisible({ timeout: 30_000 });

  mkdirSync(AUTH_DIRECTORY, { recursive: true });
  const registered: E2eUser = { ...user, displayName: `${user.firstName} ${user.lastName}` };
  writeFileSync(USER_FILE, JSON.stringify(registered));
  await page.context().storageState({ path: STORAGE_STATE });
});
