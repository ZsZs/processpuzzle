import type { Page } from '@playwright/test';

/** The host element of a routed base-entity screen: `base-form` for a details form, `base-list` for a list. */
export type ScreenHost = 'base-form' | 'base-list';

/**
 * Waits until the routed screen is in the DOM, which is what makes {@link Page.url} worth reading.
 *
 * `page.goto` resolves on the browser's `load` event — before Angular has bootstrapped, let alone routed.
 * An application whose `UrlSerializer` decorates the URL rewrites the address bar a moment after that:
 * `provideLocaleRouting` in `@processpuzzle/util` prefixes the active language, turning `/…/list` into
 * `/en/…/list`. Measured against the testbed dev server, `goto` resolved at 3116 ms, the rewrite landed at
 * 3937 ms, and the screen attached at 4326 ms — so a URL read straight after `goto` is one the application
 * is about to abandon, and the screen attaching is reliably after the rewrite.
 *
 * That gap matters because the URLs read here are not merely asserted on, they are *anchors*: an embedded
 * child's expected route is built by appending to its owner's URL, and a form's return target is the URL
 * captured before drilling into it. Anchored on the pre-rewrite URL, both then wait for an address the
 * application will never show again — which costs a whole test timeout each rather than failing fast, and
 * is what took out every EMBEDDED_COMPONENTS case when locale routing was introduced.
 *
 * Waiting on the screen rather than on the prefix keeps this ignorant of locales, which is the point: in an
 * application whose serializer adds nothing the URL never changes, and this just returns once the screen is
 * up. Nothing here has to know which serializer is installed.
 */
export async function waitForScreen(page: Page, host: ScreenHost): Promise<void> {
  await page.locator(host).first().waitFor({ state: 'attached' });
}
