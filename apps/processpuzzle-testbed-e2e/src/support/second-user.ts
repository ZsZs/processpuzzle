import { APIRequestContext, Browser, BrowserContext, expect, test } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { E2eUser } from './auth.paths';
import { signInThroughLoginPage } from './login-page';
import { serviceRoots } from './race-sample.api';

/**
 * A second person for a spec in which two people act at once: registers a new account through the API (open sign-up,
 * as `auth.setup.ts` does for the first), then signs it in through the real login form in a browser context of its
 * own. The first person is the one `auth.setup.ts` signed in, whose session every `@auth` page already carries.
 */
export async function registerAndSignInSecondUser(
  browser: Browser,
  request: APIRequestContext,
  baseURL: string,
): Promise<{ context: BrowserContext; user: E2eUser }> {
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

  // An empty session on purpose: under a project with a saved session, a plain newContext starts signed in as the
  // first person, and the login form never shows.
  const context = await browser.newContext({ baseURL, storageState: { cookies: [], origins: [] } });
  const page = await context.newPage();
  await page.goto('/auth/login');
  await signInThroughLoginPage(page, user.email, user.password);
  await page.waitForURL((url) => url.origin === new URL(baseURL).origin);
  await expect(page.locator('pp-auth-button .avatar')).toBeVisible({ timeout: 30_000 });
  await page.close();
  return { context, user };
}

/** Whether the two-person chat test runs: it registers a second account, so it is opt-in. */
export const TWO_USER_CHAT_ENABLED = process.env['PROCESSPUZZLE_E2E_TWO_USERS'] === '1';
export const twoUserChat = TWO_USER_CHAT_ENABLED ? test : test.skip;
