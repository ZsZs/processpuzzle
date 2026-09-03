import { expect } from '@playwright/test';
import { test } from '../fixtures/application-page.fixture';

test.describe('Home page structure', { tag: '@smoke' }, () => {
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

test.describe('Home page navigation', { tag: '@smoke' }, () => {
  test('Navigate to content pages', async ({ page, applicationPage }) => {
    await applicationPage.goto();
    expect(applicationPage.appContent).toBeTruthy();
    expect(page.url()).toContain('/');

    await applicationPage.navigateToUtilsPage();
    await expect(page).toHaveTitle('ProcessPuzzle Testbed - Util');
    await page.getByRole('button', { name: 'Go back' }).click();

    await applicationPage.navigateToWidgetsPage();
    await expect(page).toHaveTitle('ProcessPuzzle Testbed - Widgets');
    await page.getByRole('button', { name: 'Go back' }).click();

    await applicationPage.navigateToAuthPage();
    await expect(page).toHaveTitle('ProcessPuzzle Testbed - Auth');
    await page.getByRole('button', { name: 'Go back' }).click();

    await applicationPage.navigateToBaseEntityPage();
    await expect(page).toHaveTitle('ProcessPuzzle Testbed - Base Entity');
    await page.getByRole('button', { name: 'Go back' }).click();

    await applicationPage.navigateToCiCdPage();
    await expect(page).toHaveTitle('ProcessPuzzle Testbed - CI/CD');
    await page.getByRole('button', { name: 'Go back' }).click();
  });
});
