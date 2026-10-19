import { reflectComponentType } from '@angular/core';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { parse } from 'yaml';
import { CONTENT_BOOK_WIDGET, CONTENT_BOOK_WIDGET_REGISTRATION } from './content-book.widget';

/**
 * The checks base-widget's `widget-contract.spec.ts` applies to its own widgets, for the one widget this library
 * registers: the catalogue seeds it, and base-widget's spec leaves it to this one.
 */
const SEED_CATALOGUE = 'libs/java-shared/base-widget-backend/src/main/resources/default-widgets/processpuzzle-testbed-widgets.yaml';

describe('content-book widget contract', () => {
  const registration = CONTENT_BOOK_WIDGET_REGISTRATION;
  const mirror = reflectComponentType(registration.component);

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

  it('is seeded as a copy of its registration', () => {
    const catalogue = parse(readFileSync(resolve(process.cwd(), SEED_CATALOGUE), 'utf-8')) as { widgetDefinitions: Array<{ key: string }> };
    const { key: _key, ...seeded } = catalogue.widgetDefinitions.find((definition) => definition.key === CONTENT_BOOK_WIDGET) ?? { key: CONTENT_BOOK_WIDGET };

    expect(seeded).toEqual(JSON.parse(JSON.stringify(registration.definition)));
  });
});
