import { chromium, type FullConfig } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { resolveDependencyOrder } from '../data/dependency.resolver';

export interface CreateGlobalSetupOptions {
  /** Absolute path where the resolved registry will be written. */
  registryPath: string;
  /** Route on the application that returns the JSON descriptor registry. Defaults to '/entity-registry'. */
  registryUrl?: string;
}

export function createGlobalSetup(options: CreateGlobalSetupOptions): (config: FullConfig) => Promise<void> {
  const { registryPath, registryUrl = '/entity-registry' } = options;

  return async function globalSetup(config: FullConfig) {
    const baseURL = config.projects[0].use.baseURL;
    const browser = await chromium.launch();
    const page = await browser.newPage();

    console.log(`[global-setup] Navigating to entity registry at ${baseURL}${registryUrl}`);
    await page.goto(`${baseURL}${registryUrl}`);
    await page.waitForSelector('pre');

    const json = await page.locator('pre').textContent();
    if (!json) throw new Error(`[global-setup] Empty response from ${baseURL}${registryUrl}`);
    const raw: BaseEntityDescriptor[] = JSON.parse(json);

    const ordered = resolveDependencyOrder(raw);

    fs.mkdirSync(path.dirname(registryPath), { recursive: true });
    fs.writeFileSync(registryPath, JSON.stringify(ordered, null, 2));

    await browser.close();
  };
}
