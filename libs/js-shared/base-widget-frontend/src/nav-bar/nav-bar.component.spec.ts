import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { LayoutService } from '@processpuzzle/util';
import { describe, expect, it } from 'vitest';
import { APPLICATION_CONTEXT, NavMenuItem } from '../app-context/application-context';
import { testApplicationContext } from '../app-context/test-application-context';
import { NavBarComponent } from './nav-bar.component';

const NAV: NavMenuItem[] = [
  { id: 'home', label: 'Home', icon: 'home', routePath: 'home' },
  {
    id: 'orders',
    label: 'Orders',
    children: [
      { id: 'order-list', label: 'All orders', routePath: 'order-list' },
      { id: 'archive', label: 'Archive', children: [{ id: 'archived-orders', label: 'Archived', routePath: 'archived' }] },
    ],
  },
];

describe('NavBarComponent', () => {
  function render(props: Record<string, unknown> = {}, small = false) {
    TestBed.configureTestingModule({
      imports: [NavBarComponent],
      providers: [
        provideRouter([]),
        provideTranslocoTesting({ translations: {} }),
        { provide: LayoutService, useValue: { isSmallDevice: signal(small) } },
        { provide: APPLICATION_CONTEXT, useValue: testApplicationContext({ navItems: NAV }) },
      ],
    });
    const fixture = TestBed.createComponent(NavBarComponent);
    for (const [name, value] of Object.entries(props)) fixture.componentRef.setInput(name, value);
    fixture.detectChanges();
    return fixture;
  }

  const bar = (fixture: ReturnType<typeof render>): HTMLElement | null => fixture.nativeElement.querySelector('[data-testid="nav-bar"]');
  const topLevel = (fixture: ReturnType<typeof render>): HTMLElement[] => [...(bar(fixture)?.querySelectorAll<HTMLElement>('.pp-nav-bar__item') ?? [])];

  it('lays the top level of the application navigation out as a row, a link per routed entry', () => {
    const items = topLevel(render());

    expect(items.map((item) => item.dataset['testid'])).toEqual(['nav-bar-home', 'nav-bar-orders']);
    expect(items[0].tagName).toBe('A');
    expect(items[0].getAttribute('href')).toBe('/home');
  });

  it('opens a group as a drop-down of its whole subtree, indented by depth', () => {
    const fixture = render();
    topLevel(fixture)[1].click();
    fixture.detectChanges();

    // The menu renders into the CDK overlay, under the document body rather than the component.
    const rows = [...document.querySelectorAll<HTMLElement>('.mat-mdc-menu-panel [data-testid^="nav-bar-"]')];
    expect(rows.map((row) => row.dataset['testid'])).toEqual(['nav-bar-order-list', 'nav-bar-archive', 'nav-bar-archived-orders']);
    expect(rows[1].hasAttribute('disabled')).toBe(true);
    expect(rows[2].style.paddingInlineStart).toBe('32px');
  });

  it('gives way to the navigation menu on a small screen', () => {
    expect(bar(render({}, true))).toBeNull();
  });

  it('shows on every screen with the always visibility', () => {
    expect(bar(render({ visibility: 'always' }, true))).not.toBeNull();
  });

  it('lets the items prop override the navigation of the application', () => {
    const items = topLevel(render({ items: [{ id: 'docs', label: 'Docs', routePath: 'docs' }] }));

    expect(items.map((item) => item.dataset['testid'])).toEqual(['nav-bar-docs']);
  });

  it('renders nothing when there is nothing to navigate to', () => {
    expect(bar(render({ items: [] }))).toBeNull();
  });
});
