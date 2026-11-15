import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideMarkdown } from 'ngx-markdown';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { WorkingContextService } from '@processpuzzle/base-entity';
import { ContextObjectComponent } from './context-object.component';
import { DEMO_CONTEXT_NAME, DEMO_ROOT_NAME } from './context-object.routes';

describe('ContextObjectComponent', () => {
  let fixture: ComponentFixture<ContextObjectComponent>;
  let httpTesting: HttpTestingController;
  const active = signal<{ id: string } | undefined>(undefined);
  const unregister = vi.fn();
  const workingContext = {
    register: vi.fn(() => unregister),
    current: () => active(),
    active: signal([]),
    statusbarHostedByShell: signal(false),
    stateAttributeOf: () => undefined,
    stateOf: () => undefined,
    firing: signal(false),
  };

  beforeEach(async () => {
    (globalThis as Record<string, unknown>)['ClipboardJS'] = class {
      destroy(): void {
        /* nothing to release */
      }
    };
    active.set(undefined);
    workingContext.register.mockClear();
    unregister.mockClear();
    TestBed.configureTestingModule({
      imports: [ContextObjectComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideMarkdown({ loader: HttpClient }),
        provideRouter([]),
        provideTranslocoTesting({ translations: { en: { 'base-entity.context_object_no_context': 'Nothing selected yet.' } } }),
        { provide: WorkingContextService, useValue: workingContext },
      ],
    });
    fixture = TestBed.createComponent(ContextObjectComponent);
    httpTesting = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
    httpTesting.expectOne(fixture.componentInstance.explanationSource).flush('### Context Object');
    await fixture.whenStable();
    fixture.detectChanges();
  });

  afterEach(() => {
    httpTesting.verify();
    delete (globalThis as Record<string, unknown>)['ClipboardJS'];
  });

  const host = () => fixture.nativeElement as HTMLElement;

  it('offers the three buttons, in the order of the demonstration', () => {
    expect(Array.from(host().querySelectorAll('mat-button-toggle')).map((button) => button.textContent?.trim())).toEqual(['Aggregate Root', 'Embedded Component One', 'Embedded Component Two']);
  });

  it("declares the demonstration's context for as long as the page is open, and takes it back with the page", () => {
    expect(workingContext.register).toHaveBeenCalledTimes(1);
    expect((workingContext.register.mock.calls as unknown as Array<[Array<{ name: string; entityName: string }>]>)[0][0][0]).toMatchObject({ name: DEMO_CONTEXT_NAME, entityName: DEMO_ROOT_NAME });

    fixture.destroy();

    expect(unregister).toHaveBeenCalledTimes(1);
  });

  it("leads a part's button to the page that asks for a root while none is selected, and says so", () => {
    expect(fixture.componentInstance['componentLink']('demo-component-one')).toEqual(['ctx', DEMO_CONTEXT_NAME, 'demo-component-one']);
    expect(host().querySelector('[data-testid="context-object-no-context"]')?.textContent).toContain('Nothing selected yet.');
    expect(fixture.componentInstance.aggregateRootLink).toEqual(['aggregate-root']);
  });

  it("leads a part's button into the selected root, and drops the hint", () => {
    active.set({ id: 'R-1' });
    fixture.detectChanges();

    expect(fixture.componentInstance['componentLink']('demo-component-two')).toEqual(['ctx', DEMO_CONTEXT_NAME, 'R-1', 'demo-component-two']);
    expect(host().querySelector('[data-testid="context-object-no-context"]')).toBeNull();
  });
});
