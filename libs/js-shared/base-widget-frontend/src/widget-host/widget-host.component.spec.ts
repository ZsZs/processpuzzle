import { Component, input, output, Type } from '@angular/core';
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

describe('a lazy widget', () => {
  let fixture: ComponentFixture<WidgetHostComponent>;
  const widget: HostedWidget = { id: 'w1', type: 'lazy-widget', props: { label: 'Loaded' } };
  const lazy = (loadComponent: () => Promise<Type<unknown>>): WidgetRegistration => ({ type: 'lazy-widget', definition: { name: 'Lazy widget' }, loadComponent });
  const place = (host = TestBed.createComponent(WidgetHostComponent)) => {
    host.componentRef.setInput('widget', widget);
    host.detectChanges();
    return host;
  };
  /** A plain promise is not tracked by the zoneless scheduler, so wait for it to settle, then check the view. */
  const settle = async (host: ComponentFixture<WidgetHostComponent>) => {
    await new Promise((resolve) => setTimeout(resolve));
    host.detectChanges();
  };
  const button = (host: ComponentFixture<WidgetHostComponent>) => host.nativeElement.querySelector('.host-test-widget') as HTMLButtonElement | null;

  function configure(registry: ReadonlyMap<string, WidgetRegistration>) {
    TestBed.configureTestingModule({ imports: [WidgetHostComponent], providers: [{ provide: WIDGET_REGISTRY, useValue: registry }] });
  }

  it('shows a placeholder until its class has loaded, then renders the widget', async () => {
    configure(new Map([['lazy-widget', lazy(() => Promise.resolve(HostTestWidgetComponent))]]));
    fixture = place();

    expect(button(fixture)).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="loading-w1"]')).not.toBeNull();

    await settle(fixture);
    expect(button(fixture)?.textContent).toContain('Loaded');
  });

  it('shows the failure, and tries the import again on the next placement', async () => {
    const load = vi.fn<() => Promise<Type<unknown>>>().mockRejectedValueOnce(new Error('chunk not found')).mockResolvedValueOnce(HostTestWidgetComponent);
    configure(new Map([['lazy-widget', lazy(load)]]));
    fixture = place();
    await settle(fixture);
    expect(fixture.nativeElement.querySelector('[data-testid="load-failed-w1"]')).not.toBeNull();

    const again = place(TestBed.createComponent(WidgetHostComponent));
    await settle(again);

    expect(load).toHaveBeenCalledTimes(2);
    expect(button(again)?.textContent).toContain('Loaded');
  });

  it('renders a second placement of a loaded widget at once, without loading it again', async () => {
    const load = vi.fn(() => Promise.resolve(HostTestWidgetComponent));
    configure(new Map([['lazy-widget', lazy(load)]]));
    fixture = place();
    await settle(fixture);

    const second = place(TestBed.createComponent(WidgetHostComponent));

    expect(button(second)?.textContent).toContain('Loaded');
    expect(load).toHaveBeenCalledTimes(1);
  });
});
