import { reflectComponentType } from '@angular/core';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { parse } from 'yaml';
import { BASE_WIDGET_REGISTRATIONS } from './base-widget.providers';

/**
 * The widget contract of `docs/widget-embedding-spec.md` §2, held against every widget this library ships.
 *
 * Each registration makes three promises that nothing but this spec connects: its description matches its
 * component's inputs and outputs, and the catalogue the backend seeds carries the same description. A break
 * in any of them surfaces only when a designer places the widget — a prop the form offers and the component
 * ignores, an input no form offers, a palette entry that renders a placeholder.
 */
const SEED_CATALOGUE = 'libs/java-shared/base-widget-backend/src/main/resources/default-widgets/processpuzzle-testbed-widgets.yaml';

interface SeededDefinition {
  key: string;
  [field: string]: unknown;
}

function seededDefinitions(): SeededDefinition[] {
  return (parse(readFileSync(resolve(process.cwd(), SEED_CATALOGUE), 'utf-8')) as { widgetDefinitions: SeededDefinition[] }).widgetDefinitions;
}

const cases = BASE_WIDGET_REGISTRATIONS.map((registration) => [registration.type, registration] as const);

describe('widget contract', () => {
  it.each(cases)('%s: its inputs are exactly the properties of its props schema', (_type, registration) => {
    const inputs = reflectComponentType(registration.component)?.inputs.map((input) => input.templateName) ?? [];

    expect(inputs.sort()).toEqual(Object.keys(registration.definition.propsSchema?.properties ?? {}).sort());
  });

  it.each(cases)('%s: its outputs are exactly its output ports', (_type, registration) => {
    const outputs = reflectComponentType(registration.component)?.outputs.map((output) => output.templateName) ?? [];

    expect(outputs.sort()).toEqual((registration.definition.outputPorts ?? []).map((port) => port.name).sort());
  });

  it.each(cases)('%s: every input port names a prop', (_type, registration) => {
    const props = Object.keys(registration.definition.propsSchema?.properties ?? {});

    expect(props).toEqual(expect.arrayContaining((registration.definition.inputPorts ?? []).map((port) => port.name)));
  });

  it.each(cases)('%s: its selector carries the pp- prefix', (_type, registration) => {
    expect(reflectComponentType(registration.component)?.selector).toMatch(/^pp-/);
  });

  it('seeds exactly the widgets this library registers', () => {
    expect(
      seededDefinitions()
        .map((definition) => definition.key)
        .sort(),
    ).toEqual(BASE_WIDGET_REGISTRATIONS.map((registration) => registration.type).sort());
  });

  it.each(cases)('%s: the seeded definition is a copy of its registration', (type, registration) => {
    const { key: _key, ...seeded } = seededDefinitions().find((definition) => definition.key === type) ?? { key: type };

    // Through JSON, so an optional field the registration leaves undefined compares equal to one the YAML omits.
    expect(seeded).toEqual(JSON.parse(JSON.stringify(registration.definition)));
  });
});
