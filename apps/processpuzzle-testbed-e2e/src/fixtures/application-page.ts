import { expect, Locator, Page } from '@playwright/test';

export class ApplicationPage {
  readonly page: Page;
  readonly appFooter: Locator;
  readonly appHeader: Locator;
  readonly appRoot: Locator;
  readonly appSidenav: Locator;
  readonly navigationList: Locator;
  appContent: Locator | undefined;
  appUtils: Locator | undefined;
  baseForms: Locator | undefined;

  constructor(page: Page) {
    this.page = page;
    this.appRoot = page.locator('app-root');
    this.appHeader = page.locator('app-root > app-header');
    this.appSidenav = page.locator('app-root > mat-sidenav-container');
    this.appFooter = page.locator('app-root > app-footer');
    this.navigationList = page.locator('app-root > mat-sidenav-container mat-nav-list');
  }

  async goto() {
    await this.page.goto('/');
    await this.page.waitForURL('**/home');
    this.appContent = this.page.locator('app-root > mat-sidenav-container > app-content');
  }

  async navigateToUtilsPage() {
    expect(this.navigationList).toBeTruthy();
    await this.navigationList.locator(':nth-match(mat-list-item, 2)').click();
    await this.page.waitForURL('**/util');
    this.appUtils = this.page.locator('app-root > mat-sidenav-container > app-utils');
  }

  async navigateToBaseFormPage() {
    expect(this.navigationList).toBeTruthy();
    await this.navigationList.locator(':nth-match(mat-list-item, 3)').click();
    await this.page.waitForURL('**/base-entity');
    this.baseForms = this.page.locator('app-root > mat-sidenav-container > base-entity');
  }

  async navigateToCiCdPage() {
    expect(this.navigationList).toBeTruthy();
    await this.navigationList.locator(':nth-match(mat-list-item, 4)').click();
    await this.page.waitForURL('**/ci-cd');
    this.appUtils = this.page.locator('app-root > mat-sidenav-container > app-ci-cd');
  }
}
