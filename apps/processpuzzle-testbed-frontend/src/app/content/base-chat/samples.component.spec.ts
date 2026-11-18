import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ChatPanelComponent, ChatStore } from '@processpuzzle/base-chat';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ChatSampleOptions, ChatDefinitionOption, RecordOption } from './chat-sample-options';
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

  const options = {
    definitions: vi.fn(async (): Promise<ChatDefinitionOption[]> => []),
    records: vi.fn(async (_code: string): Promise<RecordOption[]> => []),
  };

  beforeEach(() => {
    store.openForSubject.mockClear();
    options.definitions.mockReset().mockResolvedValue([]);
    options.records.mockReset().mockResolvedValue([]);
    // The panel itself is covered by the library; here it is only asked what it was given.
    TestBed.configureTestingModule({
      providers: [provideTranslocoTesting({ translations: { en: {} } }), { provide: ChatStore, useValue: store }, { provide: ChatSampleOptions, useValue: options }],
    });
  });

  const render = async () => {
    const fixture = TestBed.createComponent(SamplesComponent);
    // The definitions arrive, then the records of the picked one: two rounds of asynchronous work.
    for (let round = 0; round < 3; round++) {
      fixture.detectChanges();
      await fixture.whenStable();
      await new Promise((resolve) => setTimeout(resolve));
    }
    fixture.detectChanges();
    const panel = fixture.debugElement.query((node) => node.componentInstance instanceof ChatPanelComponent)?.componentInstance as ChatPanelComponent;
    return { fixture, root: fixture.nativeElement as HTMLElement, panel };
  };

  const field = (root: HTMLElement, id: string) => root.querySelector(`[data-testid="${id}"]`) as HTMLInputElement;

  it('offers the seeded order chat definition and no record', async () => {
    const { root } = await render();

    expect(field(root, 'chat-sample-definition').value).toBe('order-discussion');
    expect(field(root, 'chat-sample-record').value).toBe('');
  });

  it('gives the panel no record at all while the field is empty, so that it follows the working context', async () => {
    const { panel } = await render();

    expect(panel.subjectId()).toBeUndefined();
    expect(panel.definitionKey()).toBe('order-discussion');
  });

  it('hands the panel the record the visitor types, trimmed', async () => {
    const { fixture, root, panel } = await render();

    const record = field(root, 'chat-sample-record');
    record.value = '  9d1c0000-0000-4000-8000-000000000001 ';
    record.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(panel.subjectId()).toBe('9d1c0000-0000-4000-8000-000000000001');
  });

  it('hands the panel the definition the visitor types, and none when it is cleared', async () => {
    const { fixture, root, panel } = await render();

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

  describe('with the chat definitions and records to pick from', () => {
    const orderChat: ChatDefinitionOption = { key: 'order-discussion', name: 'Order discussion', subjectType: 'ENTITY', subjectDefinition: 'order' };
    const runChat: ChatDefinitionOption = { key: 'onboarding-run-chat', name: 'Onboarding run', subjectType: 'WORKFLOW_INSTANCE', subjectDefinition: 'onboarding' };

    it('offers the definitions in a list, with the seeded one selected', async () => {
      options.definitions.mockResolvedValue([orderChat, runChat]);
      const { root } = await render();

      const select = root.querySelector('select[data-testid="chat-sample-definition"]') as HTMLSelectElement;
      expect([...select.options].map((option) => option.value)).toEqual(['order-discussion', 'onboarding-run-chat']);
      expect(select.value).toBe('order-discussion');
    });

    it("offers the picked definition's records by title, not by id, and resolves a picked title to its id", async () => {
      options.definitions.mockResolvedValue([orderChat, runChat]);
      options.records.mockResolvedValue([{ id: 'o-1', label: 'ORD-1001' }, { id: 'o-2' }]);
      const { root, fixture, panel } = await render();

      expect(options.records).toHaveBeenCalledWith('order');
      const offered = [...root.querySelectorAll('#chat-sample-records option')].map((option) => option.getAttribute('value'));
      expect(offered).toEqual(['ORD-1001', 'o-2']);

      const record = field(root, 'chat-sample-record');
      record.value = 'ORD-1001';
      record.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      expect(panel.subjectId()).toBe('o-1');
    });

    it('still takes an id typed in, and leaves the record list to the entity type of the definition', async () => {
      options.definitions.mockResolvedValue([orderChat, runChat]);
      options.records.mockResolvedValue([{ id: 'o-1', label: 'ORD-1001' }]);
      const { fixture, root, panel } = await render();
      const record = field(root, 'chat-sample-record');
      record.value = 'o-1';
      record.dispatchEvent(new Event('input'));

      const select = root.querySelector('select[data-testid="chat-sample-definition"]') as HTMLSelectElement;
      select.value = 'onboarding-run-chat';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(panel.definitionKey()).toBe('onboarding-run-chat');
      expect(panel.subjectType()).toBe('WORKFLOW_INSTANCE');
      expect(panel.subjectId()).toBeUndefined();
      expect(root.querySelectorAll('#chat-sample-records option')).toHaveLength(0);
    });

    it('offers no records when the reads fail, and the fields stay free text', async () => {
      const { root } = await render();

      expect(root.querySelector('select[data-testid="chat-sample-definition"]')).toBeNull();
      expect(root.querySelectorAll('#chat-sample-records option')).toHaveLength(0);
    });
  });
});
