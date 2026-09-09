import { expect, test, type Locator, type Page } from '@playwright/test';
import * as fs from 'node:fs';
import { EntityCrudFixtureManager, EntityFormPO, RouteResolver, type BaseEntityDescriptor } from '@processpuzzle/e2e-testing';
import { testConfig } from '../../playwright.config';
import { REGISTRY_PATH } from '../support/global-setup';

/**
 * The block editor, which the generated suites cannot reach: they drive one entity's list and Details form
 * from its descriptor, and a document's content is on neither. It lives on a tab of its own, is stored per
 * locale under `translations/{locale}/blocks` rather than on the document, and is edited by typing into
 * Tiptap instead of by filling a declared control — nothing a descriptor describes.
 *
 * Hand-written for that reason, but still built on the same fixture manager, so a document created here is
 * created exactly the way the CRUD suite creates one and is removed in teardown even when a test fails
 * halfway.
 */
const DOCUMENT_ENTITY_NAME = 'Document';

/**
 * The editing surface Tiptap mounts inside `DocumentTextBlockComponent`'s host element. Addressed by
 * contenteditable rather than by `.ProseMirror`, which is Tiptap's own class name and no contract of ours.
 */
const TEXT_BLOCK_EDITOR = '.pp-document-text-block [contenteditable="true"]';

/** Longer than DocumentContentStore's 800 ms autosave debounce, with room for the round trip after it. */
const AUTOSAVE_TIMEOUT_MS = 15_000;

const registry: BaseEntityDescriptor[] = JSON.parse(fs.readFileSync(REGISTRY_PATH, 'utf-8'));
const descriptorMap = new Map(registry.map((entityDescriptor) => [entityDescriptor.entityName, entityDescriptor]));
const routes = new RouteResolver(testConfig.routePrefix);
const documentDescriptor = requireDocumentDescriptor();

test.describe(`[${DOCUMENT_ENTITY_NAME}] CONTENT`, () => {
  test.describe.configure({ timeout: 90_000 });

  let manager: EntityCrudFixtureManager | undefined;

  test.afterEach(async ({ page }) => {
    const managerForTest = manager;
    manager = undefined;
    await managerForTest?.testTearDown(page);
  });

  test('adds a text block and autosaves what is typed into it', async ({ page }, testInfo) => {
    manager = createFixtureManager(testInfo.retry);
    const fixture = await manager.createEntity(page, documentDescriptor);
    const prose = `Autosaved by e2e ${testInfo.retry}`;

    await openContentTab(page, fixture.id);

    // A document just created has no content in any locale: the editor is empty and Add is the only way on.
    await expect(page.locator(TEXT_BLOCK_EDITOR)).toHaveCount(0);
    const addTextBlock = page.getByTestId('document-add-text-block');
    await expect(addTextBlock).toBeEnabled();

    await addTextBlock.click();
    const editor = page.locator(TEXT_BLOCK_EDITOR);
    await expect(editor).toHaveCount(1);

    await typeAndAwaitAutosave(page, editor, prose);

    // The reload is the assertion that matters: the local signal shows the text whether or not the PUT
    // landed, so only re-reading the translation from the server distinguishes a save from an echo.
    await page.reload();
    await expect(page.locator(TEXT_BLOCK_EDITOR)).toContainText(prose);
  });

  /**
   * The invariant `BaseDocumentService.update` overrides the whole-document PUT for: the Properties form
   * knows nothing about blocks, and saving it through an endpoint that takes the entire document would send
   * back the stale — here, empty — content it was loaded with and silently erase the draft.
   */
  test('keeps the content when the properties form is saved', async ({ page }, testInfo) => {
    manager = createFixtureManager(testInfo.retry);
    const fixture = await manager.createEntity(page, documentDescriptor);
    const prose = `Survives a properties save ${testInfo.retry}`;

    await openContentTab(page, fixture.id);
    await page.getByTestId('document-add-text-block').click();
    const editor = page.locator(TEXT_BLOCK_EDITOR);
    await expect(editor).toHaveCount(1);
    await typeAndAwaitAutosave(page, editor, prose);

    const form = new EntityFormPO(page, documentDescriptor, routes, descriptorMap);
    const updated = manager.buildUpdateData(documentDescriptor, fixture.data);
    const linkedIdentifications = manager.linkedIdentifications(documentDescriptor);

    await form.navigateToDetail(fixture.id);
    await form.fillForm(updated, linkedIdentifications, { skipLinked: true }, manager.contextOptions());
    await form.save();
    fixture.data = updated;

    // Both halves asserted: that the properties write went through, and that it left the blocks alone. Only
    // the pair rules out a "save" that never reached the server, which would keep the content too.
    await form.navigateToDetail(fixture.id);
    await form.assertFieldValues(updated, linkedIdentifications, manager.contextOptions());

    await openContentTab(page, fixture.id);
    await expect(page.locator(TEXT_BLOCK_EDITOR)).toContainText(prose);
  });
});

/**
 * Resolved once, at collection: this suite is about one named entity of this application, so a registry
 * without it is a broken setup rather than a case to skip — and failing here says so, instead of leaving
 * every test to fail on a missing button.
 */
function requireDocumentDescriptor(): BaseEntityDescriptor {
  const found = descriptorMap.get(DOCUMENT_ENTITY_NAME);
  if (!found) throw new Error(`'${DOCUMENT_ENTITY_NAME}' is not in the entity registry at ${REGISTRY_PATH}`);
  return found;
}

function createFixtureManager(retry: number): EntityCrudFixtureManager {
  const suffix = `e2e-${Date.now().toString(36)}-document-content-r${retry}`;
  return new EntityCrudFixtureManager(routes, descriptorMap, suffix);
}

/**
 * Reaches the editor the way a user does — through the tab bar on the Details form — rather than by going
 * straight to `document/<id>/content`. The tab is the only thing that makes the screen reachable at all, and
 * a link the container stopped rendering would otherwise pass unnoticed.
 */
async function openContentTab(page: Page, documentId: string): Promise<void> {
  const form = new EntityFormPO(page, documentDescriptor, routes, descriptorMap);
  await form.navigateToDetail(documentId);
  await page.getByTestId('document-show-content').click();
  await page.waitForURL(/\/content$/);
  await expect(page.getByTestId('document-add-text-block')).toBeVisible();
}

/**
 * Types into the block and waits for the debounced write it triggers, rather than for a fixed delay: the
 * save is a PUT of this one block, and waiting for the response is what makes the subsequent reload a
 * verification of the server's state instead of a race against it.
 */
async function typeAndAwaitAutosave(page: Page, editor: Locator, text: string): Promise<void> {
  const saved = page.waitForResponse((response) => response.request().method() === 'PUT' && /\/translations\/[a-z-]+\/blocks\/[^/]+$/.test(new URL(response.url()).pathname), {
    timeout: AUTOSAVE_TIMEOUT_MS,
  });

  await editor.click();
  await page.keyboard.type(text);

  const response = await saved;
  expect(response.ok(), `autosave of the text block failed with HTTP ${response.status()}`).toBeTruthy();
}
