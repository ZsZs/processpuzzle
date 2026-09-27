import { OverlayContainer } from '@angular/cdk/overlay';
import { Component, input, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSidenav } from '@angular/material/sidenav';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { LayoutService } from '@processpuzzle/util';
import { APPLICATION_CONTEXT, AppTitleComponent, SidenavAutosizeDirective, ThemeService, WIDGET_REGISTRY } from '@processpuzzle/widgets';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it } from 'vitest';
import { AppDefinition, RouteDefinition } from '../../domain/app-definition';
import { AppShellComponent } from './app-shell.component';

@Component({ selector: 'pp-shell-test-widget', template: `<span class="test-widget">{{ label() }}</span>` })
class ShellTestWidgetComponent {
  readonly label = input('');
}

describe('AppShellComponent', () => {
  let fixture: ComponentFixture<AppShellComponent>;
  /** The breakpoint the shell sees. A handset layout drops the nav from both of its places. */
  const smallDevice = signal(false);
  const mediumDevice = signal(false);
  /** Raised by a full-screen viewer — an image zoom, a photo album — that wants the sidenav out of the way. */
  const sidenavHidden = signal(false);

  async function render(definition: AppDefinition | undefined, withRegistry = true) {
    TestBed.configureTestingModule({
      imports: [AppShellComponent],
      // The nav region renders its labels through `ppLabel`, which injects TranslocoService. No
      // translations are registered, so every label falls back to the authored literal.
      providers: [
        provideRouter([]),
        provideTranslocoTesting({ translations: {} }),
        ...(withRegistry
          ? [
              {
                provide: WIDGET_REGISTRY,
                useValue: new Map([
                  ['test-widget', { type: 'test-widget', component: ShellTestWidgetComponent, definition: { name: 'Test widget' } }],
                  ['app-title', { type: 'app-title', component: AppTitleComponent, definition: { name: 'Application title' } }],
                ]),
              },
            ]
          : []),
        { provide: LayoutService, useValue: { isSmallDevice: smallDevice, isMediumDevice: mediumDevice, layoutClass: signal('web-layout'), isSidenavHidden: sidenavHidden, sidenavMode: signal(0) } },
      ],
    });

    fixture = TestBed.createComponent(AppShellComponent);
    fixture.componentRef.setInput('definition', definition);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function sidenav(): MatSidenav | undefined {
    return fixture.debugElement.query(By.directive(MatSidenav))?.componentInstance;
  }

  /** The host element: the shell *is* the grid, so the theme custom properties land here. */
  function shellElement(): HTMLElement {
    return fixture.nativeElement;
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    smallDevice.set(false);
    mediumDevice.set(false);
    sidenavHidden.set(false);
  });

  describe('regions', () => {
    it('renders configured header and footer widgets, the brand among them', async () => {
      await render(
        new AppDefinition({
          id: 'demo-app',
          name: 'Demo Application',
          logoUrl: '/demo-logo.svg',
          regions: [
            {
              type: 'header',
              widgets: [
                { id: 'title', type: 'app-title' },
                { id: 'language', type: 'test-widget', props: { label: 'Language' } },
              ],
            },
            { type: 'footer', widgets: [{ id: 'version', type: 'test-widget', props: { label: 'Version' } }] },
          ],
        }),
      );

      expect(fixture.nativeElement.querySelector('pp-region-header .pp-app-title')?.textContent?.trim()).toBe('Demo Application');
      expect(fixture.nativeElement.querySelector('pp-region-header .test-widget')?.textContent).toContain('Language');
      expect(fixture.nativeElement.querySelector('pp-region-footer .test-widget')?.textContent).toContain('Version');
    });

    it("provides the definition's name, logo and navigation to the widgets below it", async () => {
      await render(
        new AppDefinition({
          id: 'demo-app',
          name: 'Demo Application',
          logoUrl: '/demo-logo.svg',
          routes: [new RouteDefinition({ path: 'orders', title: 'Orders', kind: 'WIDGETS' })],
          regions: [
            {
              type: 'sidenav',
              navItems: [
                { id: 'nav-orders', label: 'Orders', routePath: 'orders' },
                { id: 'nav-gone', label: 'Gone', routePath: 'nowhere' },
              ],
            },
          ],
        }),
      );

      const context = fixture.debugElement.injector.get(APPLICATION_CONTEXT);
      expect(context.name()).toBe('Demo Application');
      expect(context.logoUrl()).toBe('/demo-logo.svg');
      // An entry naming no known route keeps its place but not its link, as in the sidenav.
      expect(context.navItems()).toEqual([
        { id: 'nav-orders', label: 'Orders', routePath: 'orders' },
        { id: 'nav-gone', label: 'Gone' },
      ]);
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

    it('orders the rows header, content, footer — the grid gives the middle row the slack', async () => {
      // jsdom does no layout, so the row order is the most of "the footer sits at the bottom" that a unit
      // test can hold onto. It is the part that regressed: with the footer anywhere but last, or the body
      // not between them, no amount of grid sizing puts it at the bottom.
      await render(new AppDefinition({ id: 'demo-app', name: 'Demo', regions: [{ type: 'header' }, { type: 'footer' }, { type: 'sidenav', navItems: [] }] }));

      const rows = [...fixture.nativeElement.children].map((node) => (node as HTMLElement).localName);
      expect(rows).toEqual(['div', 'mat-sidenav-container', 'pp-region-footer']);
      expect((fixture.nativeElement.children[0] as HTMLElement).className).toContain('pp-app-shell__top');
    });

    it('renders an outlet for the routes an app declares', async () => {
      await render(new AppDefinition({ id: 'demo-app', name: 'Demo' }));

      expect(fixture.nativeElement.querySelector('.pp-app-shell__content router-outlet')).not.toBeNull();
    });

    it('renders an empty shell before a definition resolves', async () => {
      await render(undefined);

      // Still a shell with an outlet — just no chrome, because no region has been authored yet.
      expect(fixture.nativeElement.querySelector('.pp-app-shell__content router-outlet')).not.toBeNull();
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
      expect(fixture.debugElement.query(By.directive(SidenavAutosizeDirective))).toBeTruthy();
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

    it('narrows the sidenav to compact rows on a tablet layout', async () => {
      mediumDevice.set(true);
      await render(withSidenav());

      expect(fixture.nativeElement.querySelector('.pp-app-shell__sidenav .pp-region-nav--compact')).not.toBeNull();
    });

    it('renders full rows on a wide layout', async () => {
      await render(withSidenav());

      expect(fixture.nativeElement.querySelector('.pp-app-shell__sidenav mat-nav-list')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('.pp-region-nav--compact')).toBeNull();
    });

    it('closes the sidenav while a viewer asks for the space, and reopens it after', async () => {
      await render(withSidenav());

      sidenavHidden.set(true);
      fixture.detectChanges();
      expect(sidenav()?.opened).toBe(false);

      sidenavHidden.set(false);
      fixture.detectChanges();
      expect(sidenav()?.opened).toBe(true);
    });

    // The nav-menu widget stands in for it there, reading the same entries through the APPLICATION_CONTEXT.
    it.each(['sidenav-left', 'top-nav'] as const)('renders the nav nowhere on a handset layout (%s)', async (preset) => {
      smallDevice.set(true);
      await render(withSidenav({ preset }));

      expect(sidenav()).toBeUndefined();
      expect(fixture.nativeElement.querySelector('pp-region-nav')).toBeNull();
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
          regions: [
            {
              type: 'sidenav',
              navItems: [
                { id: 'nav-orders', label: 'Orders', icon: 'receipt_long' },
                { id: 'nav-plain', label: 'Plain' },
              ],
            },
          ],
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

    it('selects the scoped Material theme by class on the host', async () => {
      await render(new AppDefinition({ id: 'demo-app', name: 'Demo', materialTheme: 'rose-red', colorScheme: 'dark' }));

      expect([...shellElement().classList]).toEqual(expect.arrayContaining(['pp-theme-rose-red', 'pp-scheme-dark']));
    });

    it('wears the processpuzzle preset when the definition names no Material theme', async () => {
      await render(new AppDefinition({ id: 'demo-app', name: 'Demo' }));

      expect([...shellElement().classList].filter((name) => name.startsWith('pp-theme-') || name.startsWith('pp-scheme-')).sort()).toEqual(['pp-scheme-light', 'pp-theme-processpuzzle']);
    });

    it('swaps the theme class when the definition is edited', async () => {
      await render(new AppDefinition({ id: 'demo-app', name: 'Demo', materialTheme: 'azure-blue' }));

      fixture.componentRef.setInput('definition', new AppDefinition({ id: 'demo-app', name: 'Demo', materialTheme: 'cyan-orange' }));
      fixture.detectChanges();

      expect([...shellElement().classList]).toContain('pp-theme-cyan-orange');
      expect([...shellElement().classList]).not.toContain('pp-theme-azure-blue');
    });

    describe('a theme the user picks', () => {
      const storageKey = 'pp-theme:demo-app';
      beforeEach(() => localStorage.removeItem(storageKey));

      function pick(preset: 'purple-green'): void {
        fixture.debugElement.injector.get(ThemeService).selectPreset(preset);
        fixture.detectChanges();
      }

      it('overrides the definition and is remembered per application', async () => {
        await render(new AppDefinition({ id: 'demo-app', name: 'Demo', materialTheme: 'azure-blue', colorScheme: 'dark' }));

        pick('purple-green');

        expect([...shellElement().classList]).toEqual(expect.arrayContaining(['pp-theme-purple-green', 'pp-scheme-dark']));
        expect(JSON.parse(localStorage.getItem(storageKey) ?? '{}')).toEqual({ preset: 'purple-green' });
      });

      it('is restored when the application is rendered again', async () => {
        localStorage.setItem(storageKey, JSON.stringify({ preset: 'purple-green' }));

        await render(new AppDefinition({ id: 'demo-app', name: 'Demo', materialTheme: 'azure-blue' }));

        expect([...shellElement().classList]).toContain('pp-theme-purple-green');
      });

      it('is only tried out, and dropped on the next edit, when the theme is not persisted', async () => {
        TestBed.configureTestingModule({ providers: [provideRouter([]), provideTranslocoTesting({ translations: {} })] });
        fixture = TestBed.createComponent(AppShellComponent);
        fixture.componentRef.setInput('persistTheme', false);
        fixture.componentRef.setInput('definition', new AppDefinition({ id: 'demo-app', name: 'Demo', materialTheme: 'azure-blue' }));
        fixture.detectChanges();

        pick('purple-green');
        expect(localStorage.getItem(storageKey)).toBeNull();

        fixture.componentRef.setInput('definition', new AppDefinition({ id: 'demo-app', name: 'Demo', materialTheme: 'rose-red' }));
        fixture.detectChanges();
        expect([...shellElement().classList]).toContain('pp-theme-rose-red');
      });
    });

    it('renders overlays inside its host, so that they wear its theme', async () => {
      await render(new AppDefinition({ id: 'demo-app', name: 'Demo', materialTheme: 'rose-red' }));

      const container = fixture.debugElement.injector.get(OverlayContainer).getContainerElement();

      expect(shellElement().contains(container)).toBe(true);
    });
  });
});

/**
 * The host `[class]` binding writes to the same element a parent template may have put its own classes
 * on — the preview does exactly that kind of outside-in styling. Angular is supposed to merge rather
 * than replace; this pins it, because a silent clobber would break a caller's layout with no error.
 */
describe('AppShellComponent host class merging', () => {
  @Component({
    selector: 'pp-shell-host',
    standalone: true,
    imports: [AppShellComponent],
    template: `<pp-app-shell class="caller-owned" [definition]="definition" />`,
  })
  class ShellHostComponent {
    definition = new AppDefinition({ id: 'demo-app', name: 'Demo', materialTheme: 'azure-blue', colorScheme: 'auto' });
  }

  it('keeps a class set by the caller alongside the theme classes it adds', async () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ imports: [ShellHostComponent], providers: [provideRouter([]), provideTranslocoTesting({ translations: {} })] });

    const hostFixture = TestBed.createComponent(ShellHostComponent);
    hostFixture.detectChanges();
    await hostFixture.whenStable();

    const shell = hostFixture.nativeElement.querySelector('pp-app-shell') as HTMLElement;
    expect([...shell.classList]).toEqual(expect.arrayContaining(['caller-owned', 'pp-theme-azure-blue', 'pp-scheme-auto']));
  });
});
