import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { EdgeRoutingMenuComponent } from './edge-routing-menu.component';
import { EdgeRoutingChoice } from './edge-routing-options';

describe('EdgeRoutingMenuComponent', () => {
  let fixture: ComponentFixture<EdgeRoutingMenuComponent>;

  const render = (active: EdgeRoutingChoice = 'orthogonal') => {
    fixture.componentRef.setInput('x', 40);
    fixture.componentRef.setInput('y', 60);
    fixture.componentRef.setInput('active', active);
    fixture.detectChanges();
  };

  const items = () => Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('[data-testid^="routing-"]'));
  const menu = () => (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('[data-testid="edge-routing-menu"]');

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EdgeRoutingMenuComponent],
      providers: [
        provideTranslocoTesting({
          translations: {
            en: {
              'base_state.state_machine_definition.modeler.routing.title': 'Routing',
              'base_state.state_machine_definition.modeler.routing.orthogonal': 'Right angles',
              'base_state.state_machine_definition.modeler.routing.polyline': 'Straight',
              'base_state.state_machine_definition.modeler.routing.bezier': 'Curved',
            },
          },
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EdgeRoutingMenuComponent);
  });

  it('offers every routing, labelled from the scope', () => {
    render();

    expect(items().map((item) => item.dataset['testid'])).toEqual(['routing-orthogonal', 'routing-polyline', 'routing-bezier']);
    expect(items().map((item) => item.textContent?.trim())).toEqual(['✓ Right angles', 'Straight', 'Curved']);
  });

  // What the edge is drawn with now, so the menu reports rather than only offers.
  it('ticks the routing in force', () => {
    render('bezier');

    expect(items().map((item) => item.getAttribute('aria-checked'))).toEqual(['false', 'false', 'true']);
  });

  it('appears where it was told to', () => {
    render();

    expect(menu()?.style.left).toBe('40px');
    expect(menu()?.style.top).toBe('60px');
  });

  it('reports what was picked', () => {
    render();
    const chosen = vi.fn();
    fixture.componentInstance.chosen.subscribe(chosen);

    items()[2].click();

    expect(chosen).toHaveBeenCalledWith('bezier');
  });

  /**
   * The two ways out. Bound on the document, because what closes a context menu is a gesture aimed
   * somewhere else — an item click closes it as well, by the same listener.
   */
  it.each([
    ['a click elsewhere', () => document.dispatchEvent(new MouseEvent('click'))],
    ['a right-click elsewhere', () => document.dispatchEvent(new MouseEvent('contextmenu'))],
    ['Escape', () => document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))],
  ])('asks to be closed by %s', (_label, gesture) => {
    render();
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);

    gesture();

    expect(closed).toHaveBeenCalled();
  });
});
