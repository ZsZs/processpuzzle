import { reflectComponentType } from '@angular/core';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { beforeAll, describe, expect, it } from 'vitest';
import { loadWidgetComponent } from '@processpuzzle/widgets';
import { parse } from 'yaml';
import { CALENDAR_WIDGET, CALENDAR_WIDGET_REGISTRATION } from './calendar/calendar.widget';

/**
 * The checks base-widget's `widget-contract.spec.ts` applies to its own widgets, for the widget this library
 * registers: the catalogue seeds it, and base-widget's spec leaves it to this one.
 */
const SEED_CATALOGUE = 'libs/java-shared/base-widget-backend/src/main/resources/default-widgets/processpuzzle-testbed-widgets.yaml';

describe.each([[CALENDAR_WIDGET, CALENDAR_WIDGET_REGISTRATION]])('%s widget contract', (key, registration) => {
  let mirror: ReturnType<typeof reflectComponentType>;
  beforeAll(async () => {
    mirror = reflectComponentType(await loadWidgetComponent(registration));
  });

  it('is registered under its own key', () => {
    expect(registration.type).toBe(key);
  });

  it('has exactly the inputs its props schema describes', () => {
    expect(mirror?.inputs.map((input) => input.templateName).sort()).toEqual(Object.keys(registration.definition.propsSchema?.properties ?? {}).sort());
  });

  it('has exactly the outputs its output ports describe', () => {
    expect(mirror?.outputs.map((output) => output.templateName).sort()).toEqual((registration.definition.outputPorts ?? []).map((port) => port.name).sort());
  });

  it('names a prop for every input port', () => {
    expect(Object.keys(registration.definition.propsSchema?.properties ?? {})).toEqual(expect.arrayContaining((registration.definition.inputPorts ?? []).map((port) => port.name)));
  });

  it('carries the pp- prefix', () => {
    expect(mirror?.selector).toMatch(/^pp-/);
  });

  it('lists no required prop, because a calendar placed with no props must not throw', () => {
    expect(registration.definition.propsSchema?.required ?? []).toEqual([]);
  });

  it('is seeded as a copy of its registration', () => {
    const catalogue = parse(readFileSync(resolve(process.cwd(), SEED_CATALOGUE), 'utf-8')) as { widgetDefinitions: Array<{ key: string }> };
    const entry = catalogue.widgetDefinitions.find((definition) => definition.key === key);
    const seeded = Object.fromEntries(Object.entries(entry ?? {}).filter(([name]) => name !== 'key'));

    expect(entry).toBeDefined();
    expect(seeded).toEqual(JSON.parse(JSON.stringify(registration.definition)));
  });
});
