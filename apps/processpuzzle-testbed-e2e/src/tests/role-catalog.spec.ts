import { expect, test, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { serviceRoots } from '../support/race-sample.api';

/**
 * The role catalog end to end: roles move between the places that refer to them (a document's editor roles, the
 * Handbook's editor controls) and Keycloak, and a user reaches them on My Profile.
 *
 * The signed-in user is shared by every `@auth` spec, and `content-book.spec.ts` relies on it holding no
 * `documentation-editor`. A test that picks a role therefore gives it back in `finally`, whatever happens.
 */
const EDITOR_ROLE = 'documentation-editor';
const HANDBOOK_SLUG = 'testbed-handbook';
const CONTENT_BOOK_LIST = '/base-document/samples/content-book/list';

test.describe('Role catalog @auth', () => {
  test.describe.configure({ timeout: 120_000 });

  test('a role picked on My Profile unlocks the Handbook editor controls, and giving it back locks them again', async ({ page }) => {
    try {
      await setRole(page, EDITOR_ROLE, true);
      await openHandbookContent(page);
      // No reload and no new sign-in: saving the roles refreshes the token, so the control appears at once.
      await expect(page.getByTestId('content-book-toolbar').getByTestId('content-book-page-edit')).toBeVisible();
    } finally {
      await setRole(page, EDITOR_ROLE, false);
    }

    await openHandbookContent(page);
    await expect(page.getByTestId('content-book-toolbar').getByTestId('content-book-page-edit')).toHaveCount(0);
  });

  test('a role an author types into a document is created in the realm and can be picked on My Profile', async ({ page, request, baseURL }) => {
    const roles = await serviceRoots(request, baseURL as string);
    const role = `e2e-role-${randomUUID().slice(0, 8)}`;
    const slug = `e2e-role-doc-${randomUUID().slice(0, 8)}`;

    // The browser's own bearer token, taken from its first backend call: the API context carries no session.
    const backendCall = page.waitForRequest((candidate) => new URL(candidate.url()).pathname.includes('/organizations/'));
    // The home page makes no backend call; an entity page does (as authentication.spec.ts relies on).
    await page.goto('/base-event/samples/event-definition');
    const authorization = (await (await backendCall).allHeaders())['authorization'];
    expect(authorization, 'bearer token of the signed-in user').toMatch(/^Bearer .+/);

    const created = await request.post(`${roles.backend}/documents`, {
      headers: { authorization },
      data: { slug, title: 'Role catalog e2e', sourceLocale: 'en', editorRoles: [role] },
    });
    expect(created.status(), `creating the document: ${await created.text()}`).toBe(201);
    const documentId = (await created.json()).id as string;

    try {
      // The role is created after the document's transaction commits, so give the realm a moment.
      await expect(async () => {
        await page.goto('/auth/my-profile');
        await expect(roleCheckbox(page, role)).toBeVisible({ timeout: 5_000 });
      }).toPass({ timeout: 45_000 });
    } finally {
      // The realm role stays: nothing in the platform removes a role on purpose, and the testbed is wiped nightly.
      await request.delete(`${roles.backend}/documents/${encodeURIComponent(documentId)}`, { headers: { authorization } });
    }
  });
});

/** The self-service checkbox of one role on My Profile. */
function roleCheckbox(page: Page, role: string) {
  return page.locator('mat-checkbox').filter({ hasText: new RegExp(`^\\s*${role}\\s*$`) });
}

/** Ticks or clears one role on My Profile and saves; resolves once the role is (or is no longer) among the user's roles. */
async function setRole(page: Page, role: string, wanted: boolean): Promise<void> {
  await page.goto('/auth/my-profile');
  const checkbox = roleCheckbox(page, role);
  await expect(checkbox, `${role} is offered on My Profile; the realm has to declare it`).toBeVisible({ timeout: 30_000 });
  const held = await checkbox.locator('input[type=checkbox]').isChecked();
  if (held !== wanted) {
    await checkbox.locator('label').click();
    await page.locator('#my-profile-save-roles').click();
  }
  const heldRoles = page.locator('ul.roles[aria-label] mat-chip').filter({ hasText: new RegExp(`^\\s*${role}\\s*$`) });
  await expect(heldRoles).toHaveCount(wanted ? 1 : 0, { timeout: 30_000 });
}

async function openHandbookContent(page: Page): Promise<void> {
  await page.goto(CONTENT_BOOK_LIST);
  await page.locator('a.link-to-details').filter({ hasText: new RegExp(`^\\s*${HANDBOOK_SLUG}\\s*$`) }).click();
  await page.getByRole('tab', { name: /content book - content/i }).click();
  await expect(page.getByTestId('content-book-toolbar')).toBeVisible();
}
