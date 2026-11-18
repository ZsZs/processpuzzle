import type { Page } from '@playwright/test';

/** How long the branded form gets to take over from the plain one before the plain one is used. */
const BRANDED_FORM_TIMEOUT_MS = 15_000;

/**
 * Fills in and submits Keycloak's sign-in page. The page carries two forms (`login.ftl`): the branded `pp-login-form`
 * Angular Element, and a plain fallback that is hidden as soon as that element is defined. Which one is on screen
 * changes while the bundle loads, so a field filled early goes into the fallback, the element then takes over with an
 * empty form, and the submit button looked up as "the first one" is the fallback's, now hidden: the click retries until
 * the test times out. Firefox, which loads the bundle later than the others, met this on every attempt.
 *
 * So the form is let settle first — the page has to be there (the application redirects to it), and the element has to
 * be defined, for at most {@link BRANDED_FORM_TIMEOUT_MS}, after which the plain form is what a stack without the bundle
 * serves — and only what is visible is used.
 */
export async function signInThroughLoginPage(page: Page, email: string, password: string): Promise<void> {
  await page.locator('#username, #pp-fallback-username').first().waitFor({ state: 'attached' });
  await page.evaluate(
    (timeout) => Promise.race([customElements.whenDefined('pp-login-form'), new Promise((resolve) => setTimeout(resolve, timeout))]),
    BRANDED_FORM_TIMEOUT_MS,
  );
  await page.locator('#username, #pp-fallback-username').filter({ visible: true }).first().fill(email);
  await page.locator('#password, #pp-fallback-password').filter({ visible: true }).first().fill(password);
  await page.locator('form [type=submit]').filter({ visible: true }).first().click();
}
