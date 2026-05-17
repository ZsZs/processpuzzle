// src/support/global-setup.ts
import { chromium, FullConfig } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import { resolveDependencyOrder } from './dependency.resolver';
import { EntityDescriptor } from '../types/entity-descriptor.types';

export const REGISTRY_PATH = path.join(__dirname, '../../tmp/entity-registry.json');

export default async function globalSetup(config: FullConfig) {
  const baseURL = config.projects[0].use.baseURL;
  const browser = await chromium.launch();
  const page = await browser.newPage();

  console.log(`[global-setup] Navigating to entity registry at ${baseURL}/entity-registry`);
  await page.goto(`${baseURL}/entity-registry`);
  await page.waitForSelector('pre');

  const json = await page.locator('pre').textContent();
  const raw: EntityDescriptor[] = JSON.parse(json!);

  const ordered = resolveDependencyOrder(raw);

  fs.mkdirSync(path.dirname(REGISTRY_PATH), { recursive: true });
  fs.writeFileSync(REGISTRY_PATH, JSON.stringify(ordered, null, 2));

  await browser.close();
}
