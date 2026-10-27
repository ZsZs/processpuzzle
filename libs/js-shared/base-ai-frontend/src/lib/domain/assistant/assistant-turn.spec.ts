import { describe, expect, it } from 'vitest';
import { parseAssistantEvents, parseCompleteAssistantEvents } from './assistant-turn';

describe('parseAssistantEvents', () => {
  it('reads the events of a turn in order', () => {
    const body = 'event:session\ndata:s-1\n\nevent:reply\ndata:Saved the draft.\n\nevent:done\ndata:\n\n';

    expect(parseAssistantEvents(body)).toEqual([
      { name: 'session', data: 's-1' },
      { name: 'reply', data: 'Saved the draft.' },
      { name: 'done', data: '' },
    ]);
  });

  it('joins the data lines of one event with newlines, as the event-stream format does', () => {
    const body = 'event:reply\ndata:First line.\ndata:Second line.\n\n';

    expect(parseAssistantEvents(body)).toEqual([{ name: 'reply', data: 'First line.\nSecond line.' }]);
  });

  it('accepts CRLF line endings', () => {
    const body = 'event:reply\r\ndata:Done.\r\n\r\n';

    expect(parseAssistantEvents(body)).toEqual([{ name: 'reply', data: 'Done.' }]);
  });

  it('skips blocks with an unknown or missing event name', () => {
    const body = 'event:other\ndata:x\n\ndata:no name\n\nevent:reply\ndata:kept\n\n';

    expect(parseAssistantEvents(body)).toEqual([{ name: 'reply', data: 'kept' }]);
  });

  it('reads an empty body as no events', () => {
    expect(parseAssistantEvents('')).toEqual([]);
  });

  it('knows the delta event, a piece of the answer on its way', () => {
    expect(parseAssistantEvents('event:delta\ndata:Saved \n\n')).toEqual([{ name: 'delta', data: 'Saved ' }]);
  });
});

describe('parseCompleteAssistantEvents', () => {
  it('reads only the blocks whose ending blank line has arrived', () => {
    const partial = 'event:session\ndata:s-1\n\nevent:delta\ndata:Saved \n\nevent:delta\ndata:the inv';

    expect(parseCompleteAssistantEvents(partial)).toEqual([
      { name: 'session', data: 's-1' },
      { name: 'delta', data: 'Saved ' },
    ]);
  });

  it('reads nothing from a body with no complete block yet', () => {
    expect(parseCompleteAssistantEvents('event:session\ndata:s-')).toEqual([]);
  });

  it('reads everything from a body that ends on a blank line', () => {
    expect(parseCompleteAssistantEvents('event:delta\ndata:a\n\nevent:delta\ndata:b\n\n')).toHaveLength(2);
  });
});
