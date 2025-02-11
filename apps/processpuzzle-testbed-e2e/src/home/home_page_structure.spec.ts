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
    expect(applicationPage.appUtils).toBeTruthy();
    expect(page.url()).toContain('/util');

    await applicationPage.navigateToBaseFormPage();
    expect(applicationPage.baseForms).toBeTruthy();
    expect(page.url()).toContain('/base-entity');

    await applicationPage.navigateToCiCdPage();
    expect(applicationPage.baseForms).toBeTruthy();
    expect(page.url()).toContain('/ci-cd');
  });
});
