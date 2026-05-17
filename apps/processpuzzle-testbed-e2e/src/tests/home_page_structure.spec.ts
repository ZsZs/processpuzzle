import { expect } from '@playwright/test';
import { test } from '../fixtures/application-page.fixture';

test.describe('Home page structure', () => {
  test('Home page', async ({ page, applicationPage }) => {
    await applicationPage.goto();
    await expect(page).toHaveTitle('ProcessPuzzle Testbed - Home');
    expect(applicationPage.appRoot).toBeTruthy();
    expect(applicationPage.appHeader).toBeTruthy();
    expect(applicationPage.appSidenav).toBeTruthy();
    expect(applicationPage.appFooter).toBeTruthy();
  });

  test('Header structure', async ({ applicationPage }) => {
    await applicationPage.goto();
    const header = applicationPage.appHeader;
    expect(header).toBeTruthy();
  });
});

test.describe('Home page navigation', () => {
  test('Navigate to content pages', async ({ page, applicationPage }) => {
    await applicationPage.goto();
    expect(applicationPage.appContent).toBeTruthy();
    expect(page.url()).toContain('/');

    await applicationPage.navigateToUtilsPage();
    await expect(page.getByRole('heading', { name: '@processpuzzle/util' })).toBeVisible();
    await page.getByRole('button', { name: 'Go back' }).click();

    await applicationPage.navigateToWidgetsPage();
    await expect(page.getByRole('heading', { name: '@processpuzzle/widgets' })).toBeVisible();
    await page.getByRole('button', { name: 'Go back' }).click();

    await applicationPage.navigateToAuthPage();
    await expect(page.getByRole('heading', { name: '@processpuzzle/auth' })).toBeVisible();
    await page.getByRole('button', { name: 'Go back' }).click();

    await applicationPage.navigateToBaseEntityPage();
    await expect(page.getByRole('heading', { name: '@processpuzzle/base-entity' })).toBeVisible();
    await page.getByRole('button', { name: 'Go back' }).click();

    await applicationPage.navigateToCiCdPage();
    await expect(page.getByRole('heading', { name: 'ProcessPuzzle Continuous Delivery' })).toBeVisible();
    await page.getByRole('button', { name: 'Go back' }).click();
  });
});
