import { describe, expect, it } from 'vitest';
import { regionWidgetTypes } from './region-widget-types';

describe('regionWidgetTypes', () => {
  it('collects the types of the widgets of every region', () => {
    const definition = {
      regions: [
        { type: 'header', widgets: [{ type: 'like-button' }, { type: 'language-selector' }] },
        { type: 'sidenav', navItems: [] },
        { type: 'footer', widgets: [{ type: 'version-button' }, { type: 'like-button' }] },
      ],
    } as never;

    expect([...regionWidgetTypes(definition)].sort()).toEqual(['language-selector', 'like-button', 'version-button']);
  });

  it('is empty without a definition or regions', () => {
    expect(regionWidgetTypes(undefined).size).toBe(0);
    expect(regionWidgetTypes({ regions: undefined }).size).toBe(0);
  });
});
