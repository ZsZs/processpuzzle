import { BrowserContext, expect, Page, test } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { PlatformApi } from '../support/platform.api';
import { EntityApi, ServiceRoots, serviceRoots } from '../support/race-sample.api';
import { STORAGE_STATE } from '../support/auth.paths';

/**
 * Two people talk about one order, each in a browser of their own: the first starts the order's chat, the second
 * finds it with its history, and what either writes reaches the other without a reload — which is the live stream
 * through the real stack (nginx, the backend, the token the application reads for `fetch`).
 */
test.describe('Chat @auth', () => {
  test.describe.configure({ timeout: 180_000 });

  let roots: ServiceRoots;
  let entities: EntityApi;
  let platform: PlatformApi;
  let orderId: string;
  let second: BrowserContext | undefined;

  test.beforeEach(async ({ request, baseURL }) => {
    roots = await serviceRoots(request, baseURL as string);
    entities = new EntityApi(request, roots);
    platform = new PlatformApi(request, roots);
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

  test('two browsers chat on one order, live', async ({ page, browser, baseURL }) => {
    await openOrderChat(page);
    await page.getByTestId('chat-start').click();
    await say(page, 'Hello from the first user');
    await expect(messageWith(page, 'Hello from the first user')).toBeVisible();

    // A second browser on the same signed-in session, as every other @auth spec starts from it: the message has to
    // reach it live, which is the point of this test. Both browsers are the same account; the messages say who sent
    // them only by name, and the test checks the text.
    second = await browser.newContext({ baseURL, storageState: STORAGE_STATE });
    const otherPage = await second.newPage();
    await openOrderChat(otherPage);
    // The second user finds the conversation, with what was said before they came.
    await expect(messageWith(otherPage, 'Hello from the first user')).toBeVisible({ timeout: 30_000 });

    await say(otherPage, 'Reply from the second user');
    await expect(messageWith(page, 'Reply from the second user')).toBeVisible({ timeout: 30_000 });

    await say(page, 'And back again');
    await expect(messageWith(otherPage, 'And back again')).toBeVisible({ timeout: 30_000 });
  });
});
