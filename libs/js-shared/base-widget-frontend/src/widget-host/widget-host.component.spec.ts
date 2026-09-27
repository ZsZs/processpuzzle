import { Component, input, output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { WIDGET_REGISTRY, WidgetRegistration } from '../widget-registry/widget-registry.token';
import { HostedWidget, resolveWidgetInputs, WidgetPortEvent } from './widget-bindings';
import { WidgetHostComponent } from './widget-host.component';

@Component({ selector: 'pp-host-test-widget', template: `<button class="host-test-widget" [title]="title()" (click)="clicked.emit(label())">{{ label() }}</button>` })
class HostTestWidgetComponent {
  readonly label = input('');
  readonly title = input('');
  readonly clicked = output<string>();
}

const REGISTRY: ReadonlyMap<string, WidgetRegistration> = new Map([['test-widget', { type: 'test-widget', component: HostTestWidgetComponent, definition: { name: 'Test widget' } }]]);

describe('resolveWidgetInputs', () => {
  afterEach(() => vi.restoreAllMocks());

  it('lets a bound value win over the static prop of the same name', () => {
    const widget: HostedWidget = { id: 'w1', type: 'test-widget', props: { label: 'static' }, inputBindings: { label: 'title' } };

    expect(resolveWidgetInputs(widget, HostTestWidgetComponent, (port) => `bound:${port}`)).toEqual({ label: 'bound:title' });
  });

  it('drops a prop the component does not declare, warning instead of failing', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);

    expect(resolveWidgetInputs({ id: 'w1', type: 'drop-test', props: { label: 'kept', stale: 1 } }, HostTestWidgetComponent)).toEqual({ label: 'kept' });
    expect(warn).toHaveBeenCalledWith(expect.stringContaining("no input 'stale'"));
  });
});

describe('WidgetHostComponent', () => {
  let fixture: ComponentFixture<WidgetHostComponent>;

  function render(widget: HostedWidget, withRegistry = true) {
    TestBed.configureTestingModule({ imports: [WidgetHostComponent], providers: withRegistry ? [{ provide: WIDGET_REGISTRY, useValue: REGISTRY }] : [] });
    fixture = TestBed.createComponent(WidgetHostComponent);
    fixture.componentRef.setInput('widget', widget);
    fixture.detectChanges();
  }

  const button = (): HTMLButtonElement => fixture.nativeElement.querySelector('.host-test-widget');

  it('renders a registered widget with its props bound', () => {
    render({ id: 'w1', type: 'test-widget', props: { label: 'Hello' } });

    expect(button().textContent).toContain('Hello');
  });

  it('pushes an edited prop into the same widget instance rather than re-creating it', () => {
    render({ id: 'w1', type: 'test-widget', props: { label: 'Before' } });
    const first = button();

    fixture.componentRef.setInput('widget', { id: 'w1', type: 'test-widget', props: { label: 'After' } });
    fixture.detectChanges();

    expect(button()).toBe(first);
    expect(button().textContent).toContain('After');
  });

  it('resolves input bindings through the container', () => {
    render({ id: 'w1', type: 'test-widget', inputBindings: { label: 'heading' } });
    fixture.componentRef.setInput('bindingResolver', (port: string) => `port ${port}`);
    fixture.detectChanges();

    expect(button().textContent).toContain('port heading');
  });

  it('preserves the widget instance when input property order changes', () => {
    render({ id: 'w1', type: 'test-widget', props: { title: 'Before title', label: 'Before' } });
    const first = button();

    fixture.componentRef.setInput('widget', { id: 'w1', type: 'test-widget', props: { label: 'After', title: 'After title' } });
    fixture.detectChanges();

    expect(button()).toBe(first);
    expect(button().textContent).toBe('After');
    expect(button().title).toBe('After title');
  });

  it('recreates the widget when an input is removed so its default is restored', () => {
    render({ id: 'w1', type: 'test-widget', props: { title: 'Custom title', label: 'Hello' } });
    const first = button();

    fixture.componentRef.setInput('widget', { id: 'w1', type: 'test-widget', props: { label: 'Hello' } });
    fixture.detectChanges();

    expect(button()).not.toBe(first);
    expect(button().textContent).toBe('Hello');
    expect(button().title).toBe('');
  });

  it('re-emits a bound output addressed to its container port', () => {
    render({ id: 'w1', type: 'test-widget', props: { label: 'Go' }, outputBindings: { clicked: 'selection' } });
    const events: WidgetPortEvent[] = [];
    fixture.componentInstance.portEmit.subscribe((event) => events.push(event));

    button().click();

    expect(events).toEqual([{ widgetId: 'w1', port: 'selection', value: 'Go' }]);
  });

  it('renders an unregistered type as a marker naming it, instead of taking the container down', () => {
    render({ id: 'ghost', type: 'entity-grid' });

    const marker = fixture.nativeElement.querySelector('[data-testid="unregistered-ghost"]');
    expect(marker.textContent).toContain('entity-grid');
    expect(marker.getAttribute('title')).toContain("'entity-grid'");
  });

  it('renders a marker rather than failing when the application registers no widget at all', () => {
    render({ id: 'w1', type: 'test-widget' }, false);

    expect(fixture.nativeElement.querySelector('[data-testid="unregistered-w1"]')).not.toBeNull();
  });
});
