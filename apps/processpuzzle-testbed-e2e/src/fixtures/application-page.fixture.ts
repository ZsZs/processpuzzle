import { test as base } from '@playwright/test';
import { ApplicationPage } from './application-page';

export const test = base.extend<{ applicationPage: ApplicationPage }>({
  applicationPage: async ({ page }, use) => {
    await use(new ApplicationPage(page));
  },
});
