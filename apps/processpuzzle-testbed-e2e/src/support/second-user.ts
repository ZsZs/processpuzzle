import { APIRequestContext, Browser, BrowserContext, expect, test } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { E2eUser } from './auth.paths';
import { serviceRoots } from './race-sample.api';

/**
 * Registers one more user and signs them in on a browser context of their own, for a spec in which two people act
 * at once — the first user is the one `auth.setup.ts` signed in, whose session every `@auth` page already carries.
 * The registration and the sign-in are the setup's: open sign-up, then the real login page.
 */
export async function signInAnotherUser(browser: Browser, request: APIRequestContext, baseURL: string): Promise<{ context: BrowserContext; user: E2eUser }> {
  const roots = await serviceRoots(request, baseURL);
  const run = randomUUID().slice(0, 8);
  const user: E2eUser = {
    email: `e2e-second-${run}@processpuzzle.local`,
    password: `E2e-${randomUUID()}`,
    firstName: 'Second',
    lastName: `Tester ${run}`,
    displayName: `Second Tester ${run}`,
  };

  let registration = await request.post(`${roots.objectStore}/registration`, { data: user });
  // Registration is rate limited per client address; the refusal says how long to wait.
  for (let attempt = 0; registration.status() === 429 && attempt < 2; attempt++) {
    const seconds = Math.min(Number(registration.headers()['retry-after']) || 30, 45);
    await new Promise((resolve) => setTimeout(resolve, seconds * 1000));
    registration = await request.post(`${roots.objectStore}/registration`, { data: user });
  }
  expect(registration.status(), `registering ${user.email}: ${await registration.text()}`).toBe(201);

  const context = await browser.newContext({ baseURL });
  const page = await context.newPage();
  await page.goto('/auth/login');
  const username = page.locator('#username, #pp-fallback-username').first();
  try {
    await username.waitFor({ state: 'visible', timeout: 60_000 });
  } catch {
    // Diagnosis for a run that cannot be watched: where the second browser actually is, and what it shows.
    await test.info().attach('second-user-login', { body: await page.screenshot(), contentType: 'image/png' });
    throw new Error(`The second user's sign-in form did not show; the page is at ${page.url()}`);
  }
  await username.fill(user.email);
  await page.locator('#password, #pp-fallback-password').first().fill(user.password);
  await page.locator('form [type=submit]').first().click();
  await page.waitForURL((url) => url.origin === new URL(baseURL).origin);
  await expect(page.locator('pp-auth-button .avatar')).toBeVisible({ timeout: 30_000 });
  await page.close();
  return { context, user };
}
