import { BrowserContext, expect, Page, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { authFilesFor, E2eUser } from '../support/auth.paths';
import { PlatformApi } from '../support/platform.api';
import { EntityApi, ServiceRoots, serviceRoots } from '../support/race-sample.api';
import { registerAndSignInSecondUser, twoUserChat } from '../support/second-user';

/**
 * Chat on one order through the real stack: a message reaches a second browser live, and the history is there for
 * whoever joins. The single-account test shares the signed-in session; the two-user test registers a second person
 * and signs them in through the login form, so the messages come from two different accounts. It is opt-in
 * (`PROCESSPUZZLE_E2E_TWO_USERS=1`), because it registers an account and signs it in.
 */
test.describe('Chat @auth', () => {
  test.describe.configure({ timeout: 180_000 });

  let roots: ServiceRoots;
  let entities: EntityApi;
  let platform: PlatformApi;
  let orderId: string;
  let second: BrowserContext | undefined;
  let first: E2eUser;

  test.beforeEach(async ({ request, baseURL }, testInfo) => {
    roots = await serviceRoots(request, baseURL as string);
    entities = new EntityApi(request, roots);
    platform = new PlatformApi(request, roots);
    first = JSON.parse(readFileSync(authFilesFor(testInfo.project.name).credentials, 'utf-8')) as E2eUser;
    const order = await entities.create('order', {
      orderNumber: `E2E-CHAT-${randomUUID().slice(0, 8)}`,
      customerName: 'Chat Customer',
      status: 'DRAFT',
      total: 10,
      lineItems: [{ productName: 'E2E item', quantity: 1, unitPrice: 10 }],
    });
    orderId = order.id;
  });

  test.afterEach(async () => {
    await second?.close();
    second = undefined;
    for (const entityId of entities.createdIds('order')) await platform.deleteWorkflowInstances({ entityId });
    await entities.removeAll();
  });

  const openOrderChat = async (page: Page) => {
    await page.goto('/base-chat/samples');
    await page.getByTestId('chat-sample-record').fill(orderId);
  };

  const say = async (page: Page, text: string) => {
    await page.getByTestId('chat-input').fill(text);
    await page.getByTestId('chat-send').click();
  };

  const messageWith = (page: Page, text: string) => page.getByTestId('chat-message').filter({ hasText: text });

  test('two browsers chat on one order, live', async ({ page, browser, baseURL }, testInfo) => {
    await openOrderChat(page);
    await page.getByTestId('chat-start').click();
    await say(page, 'Hello from the first user');
    await expect(messageWith(page, 'Hello from the first user')).toBeVisible();

    // A second browser on the same signed-in session, as every other @auth spec starts from it: the message has to
    // reach it live. Both browsers are the same account here; the two-user test below uses two accounts.
    second = await browser.newContext({ baseURL, storageState: authFilesFor(testInfo.project.name).storageState });
    const otherPage = await second.newPage();
    await openOrderChat(otherPage);
    await expect(messageWith(otherPage, 'Hello from the first user')).toBeVisible({ timeout: 30_000 });

    await say(otherPage, 'Reply from the second user');
    await expect(messageWith(page, 'Reply from the second user')).toBeVisible({ timeout: 30_000 });

    await say(page, 'And back again');
    await expect(messageWith(otherPage, 'And back again')).toBeVisible({ timeout: 30_000 });
  });

  twoUserChat('two users chat on one order, live, each under their own account', async ({ page, browser, request, baseURL }) => {
    // Two sign-ins through the real login form, plus the chat: more than the default budget of the other tests.
    test.setTimeout(300_000);
    await openOrderChat(page);
    await page.getByTestId('chat-start').click();
    await say(page, 'Hello from the first person');
    await expect(messageWith(page, 'Hello from the first person')).toBeVisible();

    // The second person is a new account, signed in through the login form in a browser of its own.
    const registered = await registerAndSignInSecondUser(browser, request, baseURL as string);
    second = registered.context;
    const otherPage = await second.newPage();
    await openOrderChat(otherPage);

    // They find the conversation with what was said before they came, and the message names its author.
    await expect(messageWith(otherPage, 'Hello from the first person')).toBeVisible({ timeout: 30_000 });
    await expect(messageWith(otherPage, 'Hello from the first person')).toContainText(first.displayName);

    await say(otherPage, 'Reply from the second person');
    await expect(messageWith(page, 'Reply from the second person')).toBeVisible({ timeout: 30_000 });
    await expect(messageWith(page, 'Reply from the second person')).toContainText(registered.user.displayName);

    await say(page, 'And back again');
    await expect(messageWith(otherPage, 'And back again')).toBeVisible({ timeout: 30_000 });
  });
});
