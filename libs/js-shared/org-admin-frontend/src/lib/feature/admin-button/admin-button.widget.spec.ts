import { reflectComponentType } from '@angular/core';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { parse } from 'yaml';
import { ADMIN_BUTTON_WIDGET, ADMIN_BUTTON_WIDGET_REGISTRATION } from './admin-button.widget';

/**
 * The checks base-widget's `widget-contract.spec.ts` applies to its own widgets, for the widget this library registers:
 * the catalogue seeds it, and base-widget's spec leaves it to this one.
 */
const SEED_CATALOGUE = 'libs/java-shared/base-widget-backend/src/main/resources/default-widgets/processpuzzle-testbed-widgets.yaml';

describe('ADMIN_BUTTON_WIDGET widget contract', () => {
  const registration = ADMIN_BUTTON_WIDGET_REGISTRATION;
  const mirror = reflectComponentType(registration.component);

  it('is registered under its own key', () => {
    expect(registration.type).toBe(ADMIN_BUTTON_WIDGET);
  });

  it('has no inputs, outputs or props', () => {
    expect(mirror?.inputs).toEqual([]);
    expect(mirror?.outputs).toEqual([]);
    expect(Object.keys(registration.definition.propsSchema?.properties ?? {})).toEqual([]);
  });

  it('carries the pp- prefix', () => {
    expect(mirror?.selector).toMatch(/^pp-/);
  });

  it('is seeded as a copy of its registration', () => {
    const catalogue = parse(readFileSync(resolve(process.cwd(), SEED_CATALOGUE), 'utf-8')) as { widgetDefinitions: Array<{ key: string }> };
    const entry = catalogue.widgetDefinitions.find((definition) => definition.key === ADMIN_BUTTON_WIDGET);
    const seeded = Object.fromEntries(Object.entries(entry ?? {}).filter(([name]) => name !== 'key'));

    expect(entry).toBeDefined();
    expect(seeded).toEqual(JSON.parse(JSON.stringify(registration.definition)));
  });
});
