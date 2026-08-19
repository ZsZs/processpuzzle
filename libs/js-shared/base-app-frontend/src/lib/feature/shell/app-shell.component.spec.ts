import { Component, input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSidenav } from '@angular/material/sidenav';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { WIDGET_REGISTRY } from '@processpuzzle/base-widget';
import { beforeEach, describe, expect, it } from 'vitest';
import { AppDefinition } from '../../domain/app-definition';
import { AppShellComponent } from './app-shell.component';

@Component({ selector: 'pp-shell-test-widget', template: `<span class="test-widget">{{ label() }}</span>` })
class ShellTestWidgetComponent {
  readonly label = input('');
}

describe('AppShellComponent', () => {
  let fixture: ComponentFixture<AppShellComponent>;

  async function render(definition: AppDefinition | undefined, withRegistry = true) {
    TestBed.configureTestingModule({
      imports: [AppShellComponent],
      providers: [provideRouter([]), ...(withRegistry ? [{ provide: WIDGET_REGISTRY, useValue: new Map([['test-widget', ShellTestWidgetComponent]]) }] : [])],
    });

    fixture = TestBed.createComponent(AppShellComponent);
    fixture.componentRef.setInput('definition', definition);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function sidenav(): MatSidenav | undefined {
    return fixture.debugElement.query(By.directive(MatSidenav))?.componentInstance;
  }

  function shellElement(): HTMLElement {
    return fixture.nativeElement.querySelector('.pp-app-shell');
  }

  beforeEach(() => TestBed.resetTestingModule());

  describe('regions', () => {
    it('renders configured header and footer widgets, and the brand from the definition', async () => {
      await render(
        new AppDefinition({
          id: 'demo-app',
          name: 'Demo Application',
          logoUrl: '/demo-logo.svg',
          regions: [
            { type: 'header', widgets: [{ id: 'language', type: 'test-widget', props: { label: 'Language' } }] },
            { type: 'footer', widgets: [{ id: 'version', type: 'test-widget', props: { label: 'Version' } }] },
          ],
        }),
      );

      expect(fixture.nativeElement.querySelector('pp-region-header img')?.getAttribute('src')).toBe('/demo-logo.svg');
      expect(fixture.nativeElement.querySelector('.pp-region-header__title')?.textContent?.trim()).toBe('Demo Application');
      expect(fixture.nativeElement.querySelector('pp-region-header .test-widget')?.textContent).toContain('Language');
      expect(fixture.nativeElement.querySelector('pp-region-footer .test-widget')?.textContent).toContain('Version');
    });

    it('does not invent header or footer regions when they are absent', async () => {
      await render(new AppDefinition({ id: 'demo-app', name: 'Demo Application' }));

      expect(fixture.nativeElement.querySelector('pp-region-header')).toBeNull();
      expect(fixture.nativeElement.querySelector('pp-region-footer')).toBeNull();
    });

    it('renders empty regions without requiring a widget registry provider', async () => {
      const definition = new AppDefinition({ id: 'demo-app', name: 'Demo Application', regions: [{ type: 'header' }, { type: 'footer' }] });

      await expect(render(definition, false)).resolves.not.toThrow();
      expect(fixture.nativeElement.querySelector('pp-region-header')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('pp-region-footer')).not.toBeNull();
    });

    it('omits a region whose type has not been chosen yet, leaving the rest rendered', async () => {
      await render(new AppDefinition({ id: 'demo-app', name: 'Demo', regions: [{ type: undefined }, { type: 'footer' }] }));

      expect(fixture.nativeElement.querySelector('pp-region-footer')).not.toBeNull();
      expect(fixture.nativeElement.querySelectorAll('pp-region-header, pp-region-nav')).toHaveLength(0);
    });

    it('renders an outlet for the routes an app declares', async () => {
      await render(new AppDefinition({ id: 'demo-app', name: 'Demo' }));

      expect(fixture.nativeElement.querySelector('.pp-app-shell__content router-outlet')).not.toBeNull();
    });

    it('renders an empty shell before a definition resolves', async () => {
      await render(undefined);

      expect(shellElement()).not.toBeNull();
      expect(fixture.nativeElement.querySelectorAll('pp-region-header, pp-region-footer, pp-region-nav')).toHaveLength(0);
    });
  });

  describe('layout', () => {
    const withSidenav = (overrides: Partial<AppDefinition> = {}) =>
      new AppDefinition({
        id: 'demo-app',
        name: 'Demo',
        regions: [{ type: 'sidenav', navItems: [{ id: 'nav-orders', label: 'Orders', routePath: 'orders' }] }],
        ...overrides,
      });

    it('renders the nav tree inside a left-hand sidenav by default', async () => {
      await render(withSidenav());

      expect(fixture.nativeElement.querySelector('mat-sidenav pp-region-nav')?.textContent).toContain('Orders');
      expect(sidenav()?.position).toBe('start');
      expect(sidenav()?.mode).toBe('side');
      expect(sidenav()?.opened).toBe(true);
    });

    it('honours the declared mode and a sidenav that starts closed', async () => {
      await render(withSidenav({ sidenavMode: 'over', sidenavOpenByDefault: false }));

      expect(sidenav()?.mode).toBe('over');
      expect(sidenav()?.opened).toBe(false);
    });

    it('places the sidenav at the end for the sidenav-right preset', async () => {
      await render(withSidenav({ preset: 'sidenav-right' }));

      expect(sidenav()?.position).toBe('end');
    });

    it('moves the nav into the header row, horizontally, for the top-nav preset', async () => {
      await render(withSidenav({ preset: 'top-nav' }));

      expect(sidenav()).toBeUndefined();
      expect(fixture.nativeElement.querySelector('.pp-app-shell__top pp-region-nav')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('.pp-region-nav--horizontal')).not.toBeNull();
    });

    it('renders the top nav even when the app declares no header region', async () => {
      // Otherwise a top-nav app whose designer never added a header would have no way to navigate.
      await render(withSidenav({ preset: 'top-nav' }));

      expect(fixture.nativeElement.querySelector('pp-region-header')).toBeNull();
      expect(fixture.nativeElement.querySelector('.pp-app-shell__top pp-region-nav')).not.toBeNull();
    });

    it('renders nested nav items, a group before the children it expands', async () => {
      await render(
        withSidenav({
          regions: [{ type: 'sidenav', navItems: [{ id: 'group', label: 'Back office', children: [{ id: 'nav-claims', label: 'Claims', routePath: 'claims' }] }] }],
        }),
      );

      const labels = [...fixture.nativeElement.querySelectorAll('pp-region-nav [matListItemTitle]')].map((node) => (node as HTMLElement).textContent?.trim());
      expect(labels).toEqual(['Back office', 'Claims']);
    });

    it('renders the icon of a nav item that declares one, and none for an item that does not', async () => {
      await render(
        withSidenav({
          regions: [{ type: 'sidenav', navItems: [{ id: 'nav-orders', label: 'Orders', icon: 'receipt_long' }, { id: 'nav-plain', label: 'Plain' }] }],
        }),
      );

      const icons = [...fixture.nativeElement.querySelectorAll('pp-region-nav [matListItemIcon]')].map((node) => (node as HTMLElement).textContent?.trim());
      expect(icons).toEqual(['receipt_long']);
    });

    it('constrains the content area to the declared maximum width', async () => {
      await render(new AppDefinition({ id: 'demo-app', name: 'Demo', contentMaxWidth: '1280px' }));

      expect((fixture.nativeElement.querySelector('.pp-app-shell__content') as HTMLElement).style.maxWidth).toBe('1280px');
    });
  });

  describe('theme', () => {
    it('applies the token overrides as custom properties, so every surface below re-tints', async () => {
      await render(new AppDefinition({ id: 'demo-app', name: 'Demo', tokenOverrides: { '--pp-surface-sidenav': '#0d1b2a' } }));

      expect(shellElement().style.getPropertyValue('--pp-surface-sidenav')).toBe('#0d1b2a');
    });

    it('sets no custom property for a definition that overrides none', async () => {
      await render(new AppDefinition({ id: 'demo-app', name: 'Demo' }));

      expect(shellElement().style.getPropertyValue('--pp-surface-sidenav')).toBe('');
    });
  });
});
