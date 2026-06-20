import { describe, expect, it } from 'vitest';
import { BaseDesktop } from './base-desktop';

describe('BaseDesktop', () => {
  it('exposes the title passed to the constructor', () => {
    const desktop = new BaseDesktop('My Workspace');

    expect(desktop.title).toBe('My Workspace');
  });

  it('defaults panels to an empty array when not provided', () => {
    const desktop = new BaseDesktop('My Workspace');

    expect(desktop.panels).toEqual([]);
    expect(desktop.panelCount()).toBe(0);
  });

  it('reports the number of panels the desktop was created with', () => {
    const desktop = new BaseDesktop('My Workspace', ['nav', 'editor', 'console']);

    expect(desktop.panelCount()).toBe(3);
  });
});
