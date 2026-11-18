import { reflectComponentType } from '@angular/core';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { parse } from 'yaml';
import { CHAT_PANEL_WIDGET, CHAT_PANEL_WIDGET_REGISTRATION } from './panel/chat-panel.widget';
import { CHAT_TOPICS_WIDGET, CHAT_TOPICS_WIDGET_REGISTRATION } from './topics/chat-topics.widget';
import { CHAT_UNREAD_BADGE_WIDGET, CHAT_UNREAD_BADGE_WIDGET_REGISTRATION } from './unread/chat-unread-badge.widget';

/**
 * The checks base-widget's `widget-contract.spec.ts` applies to its own widgets, for the two widgets this library
 * registers: the catalogue seeds them, and base-widget's spec leaves them to this one.
 */
const SEED_CATALOGUE = 'libs/java-shared/base-widget-backend/src/main/resources/default-widgets/processpuzzle-testbed-widgets.yaml';

describe.each([
  [CHAT_PANEL_WIDGET, CHAT_PANEL_WIDGET_REGISTRATION],
  [CHAT_TOPICS_WIDGET, CHAT_TOPICS_WIDGET_REGISTRATION],
  [CHAT_UNREAD_BADGE_WIDGET, CHAT_UNREAD_BADGE_WIDGET_REGISTRATION],
])('%s widget contract', (key, registration) => {
  const mirror = reflectComponentType(registration.component);

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

  it('is seeded as a copy of its registration', () => {
    const catalogue = parse(readFileSync(resolve(process.cwd(), SEED_CATALOGUE), 'utf-8')) as { widgetDefinitions: Array<{ key: string }> };
    const entry = catalogue.widgetDefinitions.find((definition) => definition.key === key);
    const seeded = Object.fromEntries(Object.entries(entry ?? {}).filter(([name]) => name !== 'key'));

    expect(entry).toBeDefined();
    expect(seeded).toEqual(JSON.parse(JSON.stringify(registration.definition)));
  });
});
