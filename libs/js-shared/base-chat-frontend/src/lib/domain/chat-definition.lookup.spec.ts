import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ChatMultiplicity } from './chat-definition';
import { ChatDefinitionLookup } from './chat-definition.lookup';
import { ChatDefinitionService } from './chat-definition.service';

describe('ChatDefinitionLookup', () => {
  const findAll = vi.fn();

  function lookup(): ChatDefinitionLookup {
    TestBed.configureTestingModule({ providers: [{ provide: ChatDefinitionService, useValue: { findAll } }] });
    return TestBed.inject(ChatDefinitionLookup);
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    findAll.mockReset();
  });

  it('knows which definitions hold topics, reading them once', async () => {
    findAll.mockReturnValue(
      of([
        { id: 'order-topics', multiplicity: ChatMultiplicity.MANY },
        { id: 'order-discussion', multiplicity: ChatMultiplicity.SINGLE },
      ]),
    );
    const subject = lookup();

    expect(await subject.hasTopics('order-topics')).toBe(true);
    expect(await subject.hasTopics('order-discussion')).toBe(false);
    expect(findAll).toHaveBeenCalledTimes(1);
  });

  it('reads a page of definitions as well', async () => {
    findAll.mockReturnValue(of({ content: [{ id: 'order-topics', multiplicity: ChatMultiplicity.MANY }] }));

    expect(await lookup().hasTopics('order-topics')).toBe(true);
  });

  it('answers no for an unknown definition', async () => {
    findAll.mockReturnValue(of([]));

    expect(await lookup().hasTopics('nothing')).toBe(false);
  });

  it('answers no when the read fails, and asks again next time', async () => {
    findAll.mockReturnValueOnce(throwError(() => new Error('down'))).mockReturnValueOnce(of([{ id: 'order-topics', multiplicity: ChatMultiplicity.MANY }]));
    const subject = lookup();

    expect(await subject.hasTopics('order-topics')).toBe(false);
    expect(await subject.hasTopics('order-topics')).toBe(true);
  });
});
