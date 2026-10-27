import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AssistantDeltaListener, AssistantService, AssistantTurnError } from '../../domain/assistant/assistant.service';
import { AssistantPanelFacade } from './assistant-panel.facade';

describe('AssistantPanelFacade', () => {
  let facade: AssistantPanelFacade;
  let turn: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    turn = vi.fn();
    TestBed.configureTestingModule({
      providers: [AssistantPanelFacade, { provide: AssistantService, useValue: { turn } }],
    });
    facade = TestBed.inject(AssistantPanelFacade);
  });

  afterEach(() => vi.clearAllMocks());

  it("shows the designer's message and the reply, and continues the same conversation next time", async () => {
    turn.mockResolvedValueOnce({ sessionId: 's-1', reply: 'Saved.' }).mockResolvedValueOnce({ sessionId: 's-1', reply: 'Renamed.' });

    await facade.send('  Define an invoice.  ');
    await facade.send('Rename it.');

    expect(facade.lines()).toEqual([
      { role: 'designer', text: 'Define an invoice.' },
      { role: 'assistant', text: 'Saved.' },
      { role: 'designer', text: 'Rename it.' },
      { role: 'assistant', text: 'Renamed.' },
    ]);
    expect(turn).toHaveBeenNthCalledWith(1, 'Define an invoice.', undefined, expect.any(Function));
    expect(turn).toHaveBeenNthCalledWith(2, 'Rename it.', 's-1', expect.any(Function));
  });

  it("shows the assistant's line as it arrives, piece by piece, then the whole reply", async () => {
    const seen: string[][] = [];
    turn.mockImplementationOnce(async (_message: string, _session: string | undefined, onDelta: AssistantDeltaListener) => {
      onDelta('Saved ');
      seen.push(facade.lines().map((line) => line.text));
      onDelta('the draft');
      seen.push(facade.lines().map((line) => line.text));
      return { sessionId: 's-1', reply: 'Saved the draft.' };
    });

    await facade.send('Define an invoice.');

    expect(seen).toEqual([
      ['Define an invoice.', 'Saved '],
      ['Define an invoice.', 'Saved the draft'],
    ]);
    expect(facade.lines()).toEqual([
      { role: 'designer', text: 'Define an invoice.' },
      { role: 'assistant', text: 'Saved the draft.' },
    ]);
  });

  it('ignores a blank message', async () => {
    await facade.send('   ');

    expect(turn).not.toHaveBeenCalled();
    expect(facade.lines()).toEqual([]);
  });

  it('reports a daily limit and keeps the conversation', async () => {
    turn.mockResolvedValueOnce({ sessionId: 's-1', reply: 'Saved.' });
    await facade.send('Define an invoice.');
    turn.mockRejectedValueOnce(new AssistantTurnError('quota'));

    await facade.send('Rename it.');

    expect(facade.failure()).toBe('quota');
    expect(turn).toHaveBeenLastCalledWith('Rename it.', 's-1', expect.any(Function));
  });

  it('starts again after the server no longer knows the conversation', async () => {
    turn.mockResolvedValueOnce({ sessionId: 's-1', reply: 'Saved.' });
    await facade.send('Define an invoice.');
    turn.mockRejectedValueOnce(new AssistantTurnError('session-not-found'));
    await facade.send('Rename it.');
    turn.mockResolvedValueOnce({ sessionId: 's-2', reply: 'New.' });

    await facade.send('Start over.');

    expect(facade.failure()).toBeUndefined();
    expect(turn).toHaveBeenLastCalledWith('Start over.', undefined, expect.any(Function));
  });

  it('is busy while a turn runs, and ignores a message sent in the meantime', async () => {
    let finish!: (value: { sessionId: string; reply: string }) => void;
    turn.mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );

    const first = facade.send('Define an invoice.');
    expect(facade.busy()).toBe(true);
    await facade.send('Also this.');
    finish({ sessionId: 's-1', reply: 'Saved.' });
    await first;

    expect(facade.busy()).toBe(false);
    expect(turn).toHaveBeenCalledTimes(1);
  });

  it('forgets the conversation on reset', async () => {
    turn.mockResolvedValueOnce({ sessionId: 's-1', reply: 'Saved.' });
    await facade.send('Define an invoice.');

    facade.reset();
    turn.mockResolvedValueOnce({ sessionId: 's-2', reply: 'New.' });
    await facade.send('Start over.');

    expect(facade.lines()).toEqual([
      { role: 'designer', text: 'Start over.' },
      { role: 'assistant', text: 'New.' },
    ]);
    expect(turn).toHaveBeenLastCalledWith('Start over.', undefined, expect.any(Function));
  });
});
