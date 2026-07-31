import { describe, expect, it } from 'vitest';
import { BaseApp } from './base-app';

describe('BaseApp', () => {
  it('exposes the title passed to the constructor', () => {
    const app = new BaseApp('My Workspace');

    expect(app.title).toBe('My Workspace');
  });

  it('defaults panels to an empty array when not provided', () => {
    const app = new BaseApp('My Workspace');

    expect(app.panels).toEqual([]);
    expect(app.panelCount()).toBe(0);
  });

  it('reports the number of panels the app was created with', () => {
    const app = new BaseApp('My Workspace', ['nav', 'editor', 'console']);

    expect(app.panelCount()).toBe(3);
  });
});
