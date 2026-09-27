import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { LayoutService } from '@processpuzzle/util';
import { describe, expect, it } from 'vitest';
import { APPLICATION_CONTEXT, NavMenuItem } from '../app-context/application-context';
import { testApplicationContext } from '../app-context/test-application-context';
import { NavMenuComponent } from './nav-menu.component';

const NAV: NavMenuItem[] = [
  { id: 'home', label: 'Home', icon: 'home', routePath: 'home' },
  { id: 'orders', label: 'Orders', children: [{ id: 'order-list', label: 'All orders', routePath: 'order-list' }] },
];

describe('NavMenuComponent', () => {
  function render(props: Record<string, unknown> = {}, small = true) {
    TestBed.configureTestingModule({
      imports: [NavMenuComponent],
      providers: [
        provideRouter([]),
        provideTranslocoTesting({ translations: {} }),
        { provide: LayoutService, useValue: { isSmallDevice: signal(small) } },
        { provide: APPLICATION_CONTEXT, useValue: testApplicationContext({ navItems: NAV }) },
      ],
    });
    const fixture = TestBed.createComponent(NavMenuComponent);
    for (const [name, value] of Object.entries(props)) fixture.componentRef.setInput(name, value);
    fixture.detectChanges();
    return fixture;
  }

  const trigger = (fixture: ReturnType<typeof render>): HTMLButtonElement | null => fixture.nativeElement.querySelector('[data-testid="nav-menu-button"]');

  /** The menu renders into the CDK overlay, under the document body rather than the component. */
  function open(fixture: ReturnType<typeof render>): HTMLElement[] {
    trigger(fixture)?.click();
    fixture.detectChanges();
    return [...document.querySelectorAll<HTMLElement>('[data-testid^="nav-menu-"]')].filter((element) => element.dataset['testid'] !== 'nav-menu-button');
  }

  it('lists the navigation of the application, a group as a disabled heading over its children', () => {
    const items = open(render());

    expect(items.map((item) => item.dataset['testid'])).toEqual(['nav-menu-home', 'nav-menu-orders', 'nav-menu-order-list']);
    expect(items[1].hasAttribute('disabled')).toBe(true);
    expect(items[2].style.paddingInlineStart).toBe('32px');
  });

  it('stays out of the way on a large screen, where the sidenav is shown', () => {
    expect(trigger(render({}, false))).toBeNull();
  });

  it('shows on every screen with the always visibility', () => {
    expect(trigger(render({ visibility: 'always' }, false))).not.toBeNull();
  });

  it('lets the items prop override the navigation of the application', () => {
    const items = open(render({ items: [{ id: 'docs', label: 'Docs', routePath: 'docs' }] }));

    expect(items.map((item) => item.dataset['testid'])).toEqual(['nav-menu-docs']);
  });
});
