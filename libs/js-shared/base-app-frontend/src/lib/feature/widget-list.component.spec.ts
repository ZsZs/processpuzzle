import { Component, input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WIDGET_REGISTRY, WidgetInstance, WidgetPlacement, WidgetRegistration } from '@processpuzzle/widgets';
import { beforeEach, describe, expect, it } from 'vitest';
import { WidgetListComponent, standaloneWidgets } from './widget-list.component';

@Component({ selector: 'pp-list-test-widget', template: `<span class="test-widget">{{ label() }}</span>` })
class ListTestWidgetComponent {
  readonly label = input('');
}

const REGISTRY: ReadonlyMap<string, WidgetRegistration> = new Map([['test-widget', { type: 'test-widget', component: ListTestWidgetComponent, definition: { name: 'Test widget' } }]]);

describe('standaloneWidgets', () => {
  it('keeps an unregistered type, rather than dropping or rejecting it', () => {
    expect(standaloneWidgets([new WidgetInstance({ id: 'w1', type: 'entity-grid' })])).toHaveLength(1);
  });

  it('omits a widget a container places by id', () => {
    const referenced = new WidgetInstance({ id: 'w2', type: 'test-widget', placement: WidgetPlacement.REFERENCED });

    expect(standaloneWidgets([referenced])).toEqual([]);
  });

  it('maps an absent list to no widgets', () => {
    expect(standaloneWidgets(undefined)).toEqual([]);
  });
});

describe('WidgetListComponent', () => {
  let fixture: ComponentFixture<WidgetListComponent>;

  async function render(widgets: WidgetInstance[], withRegistry = true) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [WidgetListComponent],
      providers: withRegistry ? [{ provide: WIDGET_REGISTRY, useValue: REGISTRY }] : [],
    });
    fixture = TestBed.createComponent(WidgetListComponent);
    fixture.componentRef.setInput('widgets', widgets);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => TestBed.resetTestingModule());

  it('renders a registered widget with its props bound', async () => {
    await render([new WidgetInstance({ id: 'w1', type: 'test-widget', props: { label: 'Hello' } })]);

    expect(fixture.nativeElement.querySelector('.test-widget').textContent).toContain('Hello');
  });

  it('renders an unregistered type as a marker naming it, instead of taking the shell down', async () => {
    await render([new WidgetInstance({ id: 'ghost', type: 'entity-grid' })]);

    const marker = fixture.nativeElement.querySelector('[data-testid="unregistered-ghost"]');
    expect(marker.textContent).toContain('entity-grid');
    expect(marker.getAttribute('title')).toContain("'entity-grid'");
  });

  it('keeps rendering the widgets around an unregistered one', async () => {
    await render([new WidgetInstance({ id: 'ghost', type: 'entity-grid' }), new WidgetInstance({ id: 'w1', type: 'test-widget', props: { label: 'Still here' } })]);

    expect(fixture.nativeElement.querySelector('.test-widget').textContent).toContain('Still here');
  });

  it('renders markers rather than failing when the application registers no widget at all', async () => {
    await expect(render([new WidgetInstance({ id: 'w1', type: 'test-widget' })], false)).resolves.not.toThrow();

    expect(fixture.nativeElement.querySelector('[data-testid="unregistered-w1"]')).not.toBeNull();
  });
});
