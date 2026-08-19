import { describe, expect, it } from 'vitest';
import { AppDefinition } from '../../domain/app-definition';
import { layoutOf, themeVarsOf } from './app-shell.model';

describe('layoutOf', () => {
  it('defaults an app that declares no layout to a left sidenav, open, in side mode', () => {
    expect(layoutOf(new AppDefinition({ id: 'demo' }))).toEqual({
      preset: 'sidenav-left',
      sidenavMode: 'side',
      sidenavPosition: 'start',
      hasSidenav: true,
      sidenavOpened: true,
      sidenavCollapsible: true,
      contentMaxWidth: undefined,
    });
  });

  it('resolves the same defaults for no definition at all, so the shell may render before one loads', () => {
    expect(layoutOf(undefined).preset).toBe('sidenav-left');
    expect(layoutOf(undefined).hasSidenav).toBe(true);
  });

  it('puts the sidenav at the end for the sidenav-right preset', () => {
    const layout = layoutOf(new AppDefinition({ preset: 'sidenav-right' }));

    expect(layout.sidenavPosition).toBe('end');
    expect(layout.hasSidenav).toBe(true);
  });

  it('gives top-nav no sidenav, whatever mode was declared', () => {
    // The mode stays resolved but unused: `hasSidenav` is the single answer to whether one renders.
    const layout = layoutOf(new AppDefinition({ preset: 'top-nav', sidenavMode: 'over' }));

    expect(layout.hasSidenav).toBe(false);
  });

  it('keeps sidenavOpenByDefault false rather than reading it as unset', () => {
    // The distinction `??` preserves and `||` would destroy: a deliberately closed sidenav.
    expect(layoutOf(new AppDefinition({ sidenavOpenByDefault: false })).sidenavOpened).toBe(false);
  });

  it('carries the declared mode and content width through', () => {
    const layout = layoutOf(new AppDefinition({ sidenavMode: 'push', contentMaxWidth: '1280px', sidenavCollapsible: false }));

    expect(layout.sidenavMode).toBe('push');
    expect(layout.contentMaxWidth).toBe('1280px');
    expect(layout.sidenavCollapsible).toBe(false);
  });

  it('falls back to the nested layout object when nothing flattened it', () => {
    // A definition built without the mapper: legal, and the shell must not render defaults for it.
    const layout = layoutOf(new AppDefinition({ layout: { preset: 'sidenav-right', sidenavMode: 'over', contentMaxWidth: '960px' } }));

    expect(layout.preset).toBe('sidenav-right');
    expect(layout.sidenavPosition).toBe('end');
    expect(layout.sidenavMode).toBe('over');
    expect(layout.contentMaxWidth).toBe('960px');
  });

  it('prefers the flattened field over the nested one, as a save does', () => {
    const layout = layoutOf(new AppDefinition({ preset: 'top-nav', layout: { preset: 'sidenav-left' } }));

    expect(layout.preset).toBe('top-nav');
  });
});

describe('themeVarsOf', () => {
  it('is empty for a definition that overrides no token', () => {
    expect(themeVarsOf(new AppDefinition({ id: 'demo' }))).toEqual({});
    expect(themeVarsOf(undefined)).toEqual({});
  });

  it('passes custom property names through untouched, leading dashes included', () => {
    const vars = themeVarsOf(new AppDefinition({ tokenOverrides: { '--pp-surface-sidenav': '#0d1b2a' } }));

    expect(vars).toEqual({ '--pp-surface-sidenav': '#0d1b2a' });
  });

  it('merges the nested and flattened overrides, the flattened one winning', () => {
    const vars = themeVarsOf(
      new AppDefinition({
        theme: { tokenOverrides: { '--pp-surface-header': '#111111', '--pp-surface-card': '#222222' } },
        tokenOverrides: { '--pp-surface-header': '#eeeeee' },
      }),
    );

    expect(vars).toEqual({ '--pp-surface-header': '#eeeeee', '--pp-surface-card': '#222222' });
  });
});
