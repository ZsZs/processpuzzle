import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it } from 'vitest';
import { NavItem } from '../../domain/app-definition';
import { RegionNavComponent, toNavRows } from './region-nav.component';

describe('toNavRows', () => {
  it('resolves an item whose path a route declares', () => {
    const rows = toNavRows([{ id: 'nav-orders', label: 'Orders', routePath: 'orders' }], ['orders']);

    expect(rows[0]).toEqual({ id: 'nav-orders', label: 'Orders', icon: undefined, routePath: 'orders', unresolved: false, children: [] });
  });

  it('flags an item whose path nothing accounts for, and gives it no target', () => {
    const rows = toNavRows([{ id: 'nav-ghost', label: 'Ghost', routePath: 'nowhere' }], ['orders']);

    expect(rows[0].unresolved).toBe(true);
    expect(rows[0].routePath).toBeUndefined();
  });

  it('trusts a path below a known one, because a module’s own routes are not knowable here', () => {
    // `back-office` is a module mount; `lines` lives in the module definition, fetched only on navigation.
    const rows = toNavRows([{ id: 'nav-lines', label: 'Lines', routePath: 'back-office/lines' }], ['back-office']);

    expect(rows[0].routePath).toBe('back-office/lines');
    expect(rows[0].unresolved).toBe(false);
  });

  it('does not treat a group node as unresolved — it expands rather than navigates', () => {
    const rows = toNavRows([{ id: 'group', label: 'Back office', children: [{ id: 'nav-orders', label: 'Orders', routePath: 'orders' }] }], ['orders']);

    expect(rows[0].unresolved).toBe(false);
    expect(rows[0].routePath).toBeUndefined();
    expect(rows[0].children[0].routePath).toBe('orders');
  });

  it('normalises surrounding slashes before resolving', () => {
    expect(toNavRows([{ id: 'a', label: 'A', routePath: '/orders/' }], ['orders'])[0].routePath).toBe('orders');
  });

  it('treats a blank path as a group node rather than an unresolved link', () => {
    const row = toNavRows([{ id: 'a', label: 'A', routePath: '   ' }], ['orders'])[0];

    expect(row.unresolved).toBe(false);
    expect(row.routePath).toBeUndefined();
  });

  it('maps an absent tree to no rows', () => {
    expect(toNavRows(undefined, [])).toEqual([]);
  });
});

describe('RegionNavComponent', () => {
  let fixture: ComponentFixture<RegionNavComponent>;

  async function render(navItems: NavItem[], knownPaths: string[] = []) {
    TestBed.resetTestingModule();
    // Transloco is required because the rows render their label through `ppLabel`, which injects
    // TranslocoService. With no translations registered every key falls back to the authored literal,
    // which is what the assertions below expect.
    TestBed.configureTestingModule({ imports: [RegionNavComponent], providers: [provideRouter([]), provideTranslocoTesting({ translations: {} })] });
    fixture = TestBed.createComponent(RegionNavComponent);
    fixture.componentRef.setInput('navItems', navItems);
    fixture.componentRef.setInput('knownPaths', knownPaths);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function item(id: string): HTMLElement {
    return fixture.nativeElement.querySelector(`[data-testid="nav-${id}"]`);
  }

  beforeEach(() => TestBed.resetTestingModule());

  it('renders a resolved item as a relative link, so the same nav works under the designer and standalone', async () => {
    await render([{ id: 'nav-orders', label: 'Orders', routePath: 'orders' }], ['orders']);

    // Relative: no leading slash, so it resolves against whichever route hosts the shell.
    expect(item('nav-orders').getAttribute('href')).toBe('/orders');
    expect(item('nav-orders').textContent).toContain('Orders');
  });

  it('renders an unresolved item as an inert row with an explanation rather than a broken link', async () => {
    await render([{ id: 'nav-ghost', label: 'Ghost', routePath: 'nowhere' }], ['orders']);

    expect(item('nav-ghost').getAttribute('href')).toBeNull();
    expect(item('nav-ghost').className).toContain('pp-region-nav__item--unresolved');
    expect(item('nav-ghost').getAttribute('title')).toContain('No route');
  });

  it('renders a group node as an inert row with no explanation', async () => {
    await render([{ id: 'group', label: 'Back office', children: [{ id: 'nav-orders', label: 'Orders', routePath: 'orders' }] }], ['orders']);

    expect(item('group').getAttribute('href')).toBeNull();
    expect(item('group').className).not.toContain('pp-region-nav__item--unresolved');
    expect(item('group').getAttribute('title')).toBe('');
    expect(item('nav-orders').getAttribute('href')).toBe('/orders');
  });

  /**
   * The icon belongs in the list item's own leading slot. Shared between the two row branches through an
   * `ngTemplateOutlet` it reached the item as one opaque node and landed in the unprojected slot beside the
   * title instead — which rendered every row of the sidenav on two lines.
   */
  it('projects the icon into the list item’s leading slot rather than beside the title', async () => {
    await render([{ id: 'nav-home', label: 'Home', icon: 'home', routePath: 'home' }], ['home']);

    const row = item('nav-home');
    expect(row.querySelector('[matListItemIcon]')?.parentElement).toBe(row);
    expect(row.querySelector('.mdc-list-item__content [matListItemIcon]')).toBeNull();
    // Material's own signal that it recognized a leading icon, and what its one-line layout keys off.
    expect(row.className).toContain('mdc-list-item--with-leading-icon');
  });

  it('projects the icon of a row that does not navigate the same way', async () => {
    await render([{ id: 'group', label: 'Back office', icon: 'work', children: [] }], []);

    const row = item('group');
    expect(row.querySelector('[matListItemIcon]')?.parentElement).toBe(row);
    expect(row.className).toContain('mdc-list-item--with-leading-icon');
  });

  it('indents a nested row below its group', async () => {
    await render([{ id: 'group', label: 'Group', children: [{ id: 'child', label: 'Child', routePath: 'orders' }] }], ['orders']);

    expect(item('group').style.paddingInlineStart).toBe('0px');
    expect(item('child').style.paddingInlineStart).toBe('12px');
  });
});
