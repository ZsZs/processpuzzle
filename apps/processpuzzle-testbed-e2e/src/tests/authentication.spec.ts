import { expect, test } from '@playwright/test';

/**
 * The login every other `@auth` spec relies on: the session set up by `auth.setup.ts` is restored, and the
 * application sends the user's bearer token to the platform's backend.
 */
test.describe('Authentication @auth', () => {
  test('the signed-in session is restored and shown in the header', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('pp-auth-button .avatar')).toBeVisible();
  });

  test('backend calls carry the bearer token, third-party calls do not', async ({ page }) => {
    const backendCall = page.waitForRequest((request) => new URL(request.url()).pathname.includes('/organizations/'));
    await page.goto('/base-event/samples/event-definition');
    const backendHeaders = await (await backendCall).allHeaders();
    expect(backendHeaders['authorization'], 'Authorization header of the first backend call').toMatch(/^Bearer .+/);

    // Test Entity is served by json-server, a third party: the platform's token must never reach it.
    // Locally that is port 3000; deployed, nginx serves it under /third-party/ on the application's own origin.
    const thirdPartyCall = page.waitForRequest((request) => {
      const url = new URL(request.url());
      return url.port === '3000' || url.pathname.startsWith('/third-party/');
    });
    await page.goto('/base-entity/samples/test-entity/list');
    expect((await (await thirdPartyCall).allHeaders())['authorization']).toBeUndefined();
  });
});
