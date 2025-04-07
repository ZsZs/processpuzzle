import { expect, Locator, Page } from '@playwright/test';

export class ApplicationPage {
  readonly page: Page;
  readonly appFooter: Locator;
  readonly appHeader: Locator;
  readonly appRoot: Locator;
  readonly appSidenav: Locator;
  readonly navigationList: Locator;
  appContent: Locator | undefined;

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

  async navigateBack() {
    await this.page.getByRole('button', { name: 'Go back' }).click();
  }

  async navigateToUtilsPage() {
    await this.navigateToPage('See Utils Library...', '**/util');
  }

  async navigateToWidgetsPage() {
    await this.navigateToPage('See Widgets Library...', '**/widgets');
  }

  async navigateToBaseEntityPage() {
    await this.navigateToPage('See Base Entity Library...', '**/base-entity');
  }

  async navigateToCiCdPage() {
    await this.navigateToPage('See CI/CD Pipeline...', '**/ci-cd');
  }

  // region private helper methods
  private async navigateToPage(buttonName: string, expectedUrl: string) {
    expect(this.navigationList).toBeTruthy();
    await this.page.getByRole('button', { name: buttonName }).click();
    await this.page.waitForURL(expectedUrl);
  }

  // endregion
}
