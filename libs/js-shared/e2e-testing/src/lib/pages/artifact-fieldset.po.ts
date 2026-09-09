import { expect, type Locator, type Page, type Request, type Response } from '@playwright/test';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import type { ArtifactControlTester } from '../controls/control-tester';
import { formControlTestId } from '../selectors/test-id';
import { exactText } from '../selectors/text-match';

export interface ArtifactFieldsetPOOptions {
  /** Overrides Playwright's default expect timeout — an upload is a round trip to the object store. */
  expectTimeoutMs?: number;
}

/** A file to upload, as `setInputFiles` takes it: bytes rather than a path on disk. */
export interface ArtifactUpload {
  name: string;
  mimeType: string;
  buffer: Buffer;
}

/**
 * Every adapter posts an upload to `<base>/objects` — `ObjectStoreService` appends that path to whichever base
 * URL the runtime configuration names — so one pattern covers `processpuzzle-store` and the Cloud Function
 * alike. Longer paths (`/objects/:bucket/:id`) do not match, and the handler checks the method as well.
 */
const UPLOAD_ROUTE_GLOB = '**/objects';

/** The same endpoint as {@link UPLOAD_ROUTE_GLOB}, as a matcher for the URL an observed request was sent to. */
const UPLOAD_URL_PATTERN = /\/objects(?:\?.*)?$/;

/** How much of a refused upload's response body is worth quoting in a failure message. */
const BODY_EXCERPT_LENGTH = 200;

/**
 * Collects whatever went wrong with an upload, so that a row which never appears can say *why*. Registered
 * around the click that uploads and read back if the wait for the row then fails.
 */
interface RefusedUploadWatch {
  /** What was seen, plus whatever the application is saying on screen — `undefined` if there is nothing to add. */
  diagnose(): Promise<string | undefined>;
  dispose(): void;
}

/** `POST …/objects → 401 Unauthorized: Invalid IAP credentials: empty token` — status, and the body that names the cause. */
async function describeRefusal(response: Response): Promise<string> {
  const body = await response
    .text()
    .then((text) => text.replace(/\s+/g, ' ').trim())
    .catch(() => '');
  const excerpt = body.length > BODY_EXCERPT_LENGTH ? `${body.slice(0, BODY_EXCERPT_LENGTH)}…` : body;
  return `POST ${response.url()} → ${response.status()} ${response.statusText()}${excerpt ? `: ${excerpt}` : ''}`;
}

/**
 * An upload that got no response at all — the case a status code cannot describe: a CORS pre-flight the backend's
 * allow-list does not cover, a refused connection, an unresolvable host. The browser reports these to the
 * application as HTTP status `0`, which is how they reach the same snackbar a genuine rejection does.
 */
function describeNetworkFailure(request: Request): string {
  return `POST ${request.url()} → no response: ${request.failure()?.errorText ?? 'unknown network failure'}`;
}

/**
 * Prepends `diagnosis` to a Playwright assertion failure, keeping the locator, the call log and the code frame
 * the original carries — the reporter prints `stack`, so a message left un-prefixed there would not be shown.
 */
function annotate(error: unknown, diagnosis: string): unknown {
  if (!(error instanceof Error)) return error;
  const prefix = `${diagnosis}\n\n`;
  error.message = prefix + error.message;
  if (error.stack) error.stack = prefix + error.stack;
  return error;
}

/**
 * The `fieldset` an `ARTIFACT` attribute renders: at most one row naming the stored file, and the selector that
 * uploads a new one.
 *
 * Shaped after {@link RelationshipFieldsetPO}, whose control it resembles — a fieldset, a `<ul>`, an action
 * revealed by focus — and differs from in the two ways that matter here: the list holds one row rather than
 * many, and the row's content lives in the object store rather than in the entity, so a row appearing means an
 * upload completed and a row disappearing means an object was deleted.
 */
export class ArtifactFieldsetPO {
  constructor(
    private readonly page: Page,
    private readonly ownerDescriptor: BaseEntityDescriptor,
    private readonly tester: ArtifactControlTester,
    private readonly options: ArtifactFieldsetPOOptions = {},
  ) {}

  // ── Locators ────────────────────────────────────────────────────

  /** The control host, which is what carries `data-testid` (`BaseFormControlComponent.testId`). */
  fieldset(): Locator {
    return this.page.getByTestId(formControlTestId(this.ownerDescriptor.entityName, this.tester.attr.attrName));
  }

  rows(): Locator {
    return this.fieldset().locator('li');
  }

  /** The row naming `fileName` — a row's link text is the artifact's display name. */
  row(fileName: string): Locator {
    return this.rows()
      .filter({ has: this.page.locator('a').filter({ hasText: exactText(fileName) }) })
      .first();
  }

  thumbnail(): Locator {
    return this.fieldset().locator('img.artifact-thumbnail');
  }

  mimeIcon(): Locator {
    return this.fieldset().locator('mat-icon.artifact-icon');
  }

  /**
   * The `<fieldset>` inside the control host — the host carries the test id, this carries `tabindex`, so this
   * is where focus has to land.
   */
  private focusTarget(): Locator {
    return this.fieldset().locator('fieldset.base-entity-form-field').first();
  }

  revealSelectorButton(): Locator {
    return this.fieldset().getByRole('button', { name: this.tester.revealSelectorButtonName, exact: true });
  }

  fileInput(): Locator {
    return this.fieldset().locator('input[type="file"]');
  }

  nameInput(): Locator {
    return this.fieldset().getByPlaceholder('Artifact name');
  }

  mimeTypeInput(): Locator {
    return this.fieldset().getByPlaceholder('MIME type');
  }

  /** Exact, because `Upload file` — the button that reveals this one — would otherwise match as well. */
  uploadButton(): Locator {
    return this.fieldset().getByRole('button', { name: this.tester.uploadButtonName, exact: true });
  }

  cancelButton(): Locator {
    return this.fieldset().getByRole('button', { name: 'Cancel', exact: true });
  }

  /**
   * The snackbar the control reports a failed store call through — an overlay, so page-level rather than in the
   * fieldset.
   *
   * Scoped away from `.error-snackbar` because a refused upload raises two snackbars, not one:
   * `centralHttpErrorInterceptor` reports the response through `CentralErrorHandler` (panel class
   * `error-snackbar`) and the control reports the upload. `MatSnackBar` shows one at a time, so opening the
   * second dismisses the first — but the first stays in the DOM for the length of its exit animation, and an
   * unscoped `mat-snack-bar-container` is a strict-mode violation for as long as that window lasts.
   */
  notification(): Locator {
    return this.page.locator('mat-snack-bar-container:not(.error-snackbar)');
  }

  /** Every snackbar, the central error handler's included — what has to be gone before the controls beneath are clickable. */
  private anyNotification(): Locator {
    return this.page.locator('mat-snack-bar-container');
  }

  /** The central error handler's snackbar — the one carrying the server's `errorText`, as opposed to the control's own wording. */
  private errorNotification(): Locator {
    return this.page.locator('mat-snack-bar-container.error-snackbar');
  }

  private notificationDismissButton(): Locator {
    return this.notification().getByRole('button', { name: 'Close', exact: true });
  }

  // ── Actions ─────────────────────────────────────────────────────

  /**
   * Focuses the fieldset, which is what reveals the button opening the selector.
   *
   * The button is `display: none` until `.base-entity-form-field:focus-within` matches, and a hidden element is
   * not in the accessibility tree — so without this a role query does not find it invisible, it does not find
   * it at all.
   */
  async focusFieldset(): Promise<void> {
    await expect(this.focusTarget()).toBeVisible(this.expectOptions());
    await this.focusTarget().focus();
  }

  async openSelector(): Promise<void> {
    await this.focusFieldset();
    await expect(this.revealSelectorButton()).toBeVisible(this.expectOptions());
    await this.revealSelectorButton().click();
    await expect(this.fileInput()).toBeVisible(this.expectOptions());
  }

  /**
   * Picks a file, which fills the name and MIME type from it — the two text inputs are left alone so that the
   * derivation is what the assertions below see.
   */
  async chooseFile(upload: ArtifactUpload): Promise<void> {
    await this.fileInput().setInputFiles({ name: upload.name, mimeType: upload.mimeType, buffer: upload.buffer });
  }

  /**
   * Opens the selector, picks the file, uploads it, and waits for the row the upload produces.
   *
   * The wait is watched rather than plain because a refused upload and a broken control look the same from here —
   * no row — and the bare assertion reports only `element(s) not found`, which sent a reader of a CI failure to
   * the Playwright trace to find a `401` from the object store that the browser had known all along. A refusal is
   * now named in the failure message instead.
   */
  async uploadFile(upload: ArtifactUpload): Promise<void> {
    await this.openSelector();
    await this.chooseFile(upload);
    await expect(this.uploadButton()).toBeEnabled(this.expectOptions());

    const watch = this.watchForRefusedUpload();
    try {
      await this.uploadButton().click();
      await this.assertArtifact(upload.name);
    } catch (error) {
      const diagnosis = await watch.diagnose();
      throw diagnosis === undefined ? error : annotate(error, diagnosis);
    } finally {
      watch.dispose();
    }
  }

  async cancelSelector(): Promise<void> {
    await this.cancelButton().click();
    await expect(this.fileInput()).toHaveCount(0, this.expectOptions());
  }

  /**
   * Attempts an upload with the store made to refuse it, and asserts the control reports the failure instead of
   * closing the selector as though the user had cancelled.
   *
   * Worth a step of its own because the two outcomes are otherwise indistinguishable on screen: an object store
   * that is unreachable — a private Cloud Run service, a wrong base URL, an expired credential — leaves exactly
   * the display a cancelled upload does, so nothing in the suite would fail on the difference. The refusal is
   * injected in the browser, which is what lets it hold for both adapters without either being misconfigured.
   *
   * The refusal is injected as the `{errorId, errorText}` body both backends actually return, so the step also
   * covers the path production takes: the error snackbar has to show the server's own `errorText` rather than
   * Angular's synthesized "Http failure response for …". A `text/plain` body would exercise a fallback branch
   * instead, and passed for years while every real server message was being discarded.
   *
   * Leaves the fieldset as it was found: notification dismissed, selector closed, no artifact.
   */
  async assertUploadFailureIsReported(upload: ArtifactUpload): Promise<void> {
    const errorText = 'e2e: the object store refused this upload';
    await this.page.route(UPLOAD_ROUTE_GLOB, async (route) => {
      if (route.request().method() !== 'POST') return route.fallback();
      await route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ errorId: 'store.access-denied', errorText }) });
    });

    try {
      await this.openSelector();
      await this.chooseFile(upload);
      await expect(this.uploadButton()).toBeEnabled(this.expectOptions());
      await this.uploadButton().click();

      await expect(this.notification()).toBeVisible(this.expectOptions());
      await expect(this.errorNotification()).toContainText(errorText, this.expectOptions());
      // The selection survives, so the user can retry without picking the file again.
      await expect(this.fileInput()).toBeVisible(this.expectOptions());
      await expect(this.uploadButton()).toBeEnabled(this.expectOptions());
      await this.assertNoArtifact();
    } finally {
      await this.page.unroute(UPLOAD_ROUTE_GLOB);
    }

    await this.dismissNotification();
    await this.cancelSelector();
  }

  /**
   * Starts collecting refused uploads. The responses are captured as they arrive rather than looked for after the
   * failure, because the snackbar reporting them lives five seconds and the wait for the row outlasts it — read
   * late, the screen has often gone quiet again and the screenshot shows an ordinary form.
   */
  private watchForRefusedUpload(): RefusedUploadWatch {
    const isUpload = (request: Request) => request.method() === 'POST' && UPLOAD_URL_PATTERN.test(request.url());
    const refused: Response[] = [];
    const failed: Request[] = [];

    const onResponse = (response: Response) => {
      if (isUpload(response.request()) && response.status() >= 400) refused.push(response);
    };
    const onRequestFailed = (request: Request) => {
      if (isUpload(request)) failed.push(request);
    };
    this.page.on('response', onResponse);
    this.page.on('requestfailed', onRequestFailed);

    return {
      dispose: () => {
        this.page.off('response', onResponse);
        this.page.off('requestfailed', onRequestFailed);
      },
      diagnose: async () => {
        const problems = [...(await Promise.all(refused.map(describeRefusal))), ...failed.map(describeNetworkFailure)];
        const reported = await this.notificationText();
        if (!problems.length && reported === undefined) return undefined;

        return [
          problems.length
            ? `The upload did not succeed: ${problems.join('; ')}`
            : 'No upload was refused and none failed to reach the store, so suspect the control rather than the backend.',
          reported === undefined ? undefined : `The application reported: "${reported}"`,
        ]
          .filter((line) => line !== undefined)
          .join('\n');
      },
    };
  }

  /**
   * Whatever the snackbars are saying, the central error handler's included — its message names the HTTP status,
   * the control's names the failed upload, and either is worth quoting. `allInnerTexts` rather than a locator
   * assertion so that two open snackbars are not a strict-mode violation while the first animates out.
   */
  private async notificationText(): Promise<string | undefined> {
    const texts = await this.anyNotification()
      .allInnerTexts()
      .catch(() => [] as string[]);
    const joined = texts
      .map((text) => text.replace(/\s+/g, ' ').trim())
      .filter(Boolean)
      .join(' | ');
    return joined || undefined;
  }

  /**
   * Closes the notification so it cannot overlay the controls beneath it — tolerantly, because the snackbar
   * also dismisses itself on a timer and may well be gone already.
   *
   * Waits on every snackbar rather than only the control's: the central error handler's has its own `Close`
   * button which this does not press, so it goes on its own timer, and it overlays the controls until it does.
   */
  async dismissNotification(): Promise<void> {
    await expect(async () => {
      if (await this.notification().count()) await this.notificationDismissButton().click({ timeout: 1000 });
      await expect(this.anyNotification()).toHaveCount(0, { timeout: 1000 });
    }).toPass({ timeout: this.options.expectTimeoutMs ?? 15_000 });
  }

  /**
   * Removes the artifact, confirming the dialog — which is what makes the object store call: the control
   * deletes the stored object first and clears the reference only once that succeeds.
   */
  async removeArtifact(fileName: string): Promise<void> {
    const deleteButton = this.row(fileName).getByRole('button', { name: this.tester.rowDeleteAriaLabel }).first();
    const confirmButton = this.page.getByTestId('delete-confirmation-confirm');

    await expect(async () => {
      await deleteButton.click();
      await expect(confirmButton).toBeVisible({ timeout: 2000 });
    }).toPass({ timeout: this.options.expectTimeoutMs ?? 15_000 });

    await confirmButton.click();
    await expect(confirmButton).toBeHidden(this.expectOptions());
  }

  /**
   * Follows the artifact link and returns the URI the tab it opens was given.
   *
   * The control resolves that URI from the object store and hands it to `window.open(..., 'noopener')`, which
   * leaves the new page with no opener — so it arrives on the browser context, not as a popup of this page.
   */
  async openArtifact(fileName: string): Promise<string> {
    const context = this.page.context();
    const opened = context.waitForEvent('page');
    await this.row(fileName).locator('a').first().click();

    const artifactPage = await opened;
    await artifactPage.waitForLoadState('domcontentloaded').catch(() => undefined);
    const uri = artifactPage.url();
    await artifactPage.close();
    return uri;
  }

  // ── Assertions ──────────────────────────────────────────────────

  async assertArtifact(fileName: string): Promise<void> {
    await expect(this.row(fileName)).toBeVisible(this.expectOptions());
  }

  async assertNoArtifact(): Promise<void> {
    await expect(this.rows()).toHaveCount(0, this.expectOptions());
  }

  /** A raster image is shown as a downscaled preview, which is the object store's thumbnail being served. */
  async assertThumbnail(): Promise<void> {
    const thumbnail = this.thumbnail();
    await expect(thumbnail).toBeVisible(this.expectOptions());
    // `src` bound to a resolved URI rather than left empty is what tells a rendered thumbnail from the
    // control merely having reserved the element.
    await expect(thumbnail).toHaveAttribute('src', /\S/, this.expectOptions());
  }

  /** Anything else falls back to an icon standing for its MIME type. */
  async assertMimeIcon(icon: string): Promise<void> {
    await expect(this.mimeIcon()).toHaveText(icon, this.expectOptions());
    await expect(this.thumbnail()).toHaveCount(0, this.expectOptions());
  }

  /** With the fieldset unfocused the action is hidden on every control, so focus first or this proves nothing. */
  async assertSelectorRevealedOnFocus(): Promise<void> {
    await expect(this.revealSelectorButton()).toBeHidden(this.expectOptions());
    await this.focusFieldset();
    await expect(this.revealSelectorButton()).toBeVisible(this.expectOptions());
  }

  private expectOptions(): { timeout?: number } | undefined {
    return this.options.expectTimeoutMs === undefined ? undefined : { timeout: this.options.expectTimeoutMs };
  }
}
