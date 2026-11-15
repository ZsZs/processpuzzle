import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ChatPanelComponent, ChatStore } from '@processpuzzle/base-chat';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SamplesComponent } from './samples.component';

describe('base-chat SamplesComponent', () => {
  const store = {
    connection: signal('idle'),
    connect: vi.fn(),
    release: vi.fn(),
    setViewing: vi.fn(),
    viewOf: () => undefined,
    openForSubject: vi.fn(async () => undefined),
  };

  beforeEach(() => {
    store.openForSubject.mockClear();
    // The panel itself is covered by the library; here it is only asked what it was given.
    TestBed.configureTestingModule({
      providers: [provideTranslocoTesting({ translations: { en: {} } }), { provide: ChatStore, useValue: store }],
    });
  });

  const render = () => {
    const fixture = TestBed.createComponent(SamplesComponent);
    fixture.detectChanges();
    const panel = fixture.debugElement.query((node) => node.componentInstance instanceof ChatPanelComponent)?.componentInstance as ChatPanelComponent;
    return { fixture, root: fixture.nativeElement as HTMLElement, panel };
  };

  const field = (root: HTMLElement, id: string) => root.querySelector(`[data-testid="${id}"]`) as HTMLInputElement;

  it('offers the seeded order chat definition and no record', () => {
    const { root } = render();

    expect(field(root, 'chat-sample-definition').value).toBe('order-discussion');
    expect(field(root, 'chat-sample-record').value).toBe('');
  });

  it('gives the panel no record at all while the field is empty, so that it follows the working context', () => {
    const { panel } = render();

    expect(panel.subjectId()).toBeUndefined();
    expect(panel.definitionKey()).toBe('order-discussion');
  });

  it('hands the panel the record the visitor types, trimmed', () => {
    const { fixture, root, panel } = render();

    const record = field(root, 'chat-sample-record');
    record.value = '  9d1c0000-0000-4000-8000-000000000001 ';
    record.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(panel.subjectId()).toBe('9d1c0000-0000-4000-8000-000000000001');
  });

  it('hands the panel the definition the visitor types, and none when it is cleared', () => {
    const { fixture, root, panel } = render();

    const definition = field(root, 'chat-sample-definition');
    definition.value = 'race-protests';
    definition.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(panel.definitionKey()).toBe('race-protests');

    definition.value = '';
    definition.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(panel.definitionKey()).toBeUndefined();
  });
});
