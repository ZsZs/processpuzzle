import { describe, expect, it } from 'vitest';
import { SseParser } from './sse-parser';

describe('SseParser', () => {
  it('reads an event the way the server writes it, with no space after the colon', () => {
    expect(new SseParser().feed('id:c1:4\nevent:message.posted\ndata:{"a":1}\n\n')).toEqual([{ id: 'c1:4', event: 'message.posted', data: '{"a":1}' }]);
  });

  it('allows one optional space after the colon and strips only one', () => {
    expect(new SseParser().feed('event: ping\ndata:  two spaces\n\n')).toEqual([{ id: undefined, event: 'ping', data: ' two spaces' }]);
  });

  it('names an event with no event field "message"', () => {
    expect(new SseParser().feed('data: hello\n\n')).toEqual([{ id: undefined, event: 'message', data: 'hello' }]);
  });

  it('joins several data lines with a line feed', () => {
    expect(new SseParser().feed('data: one\ndata: two\n\n')[0]?.data).toBe('one\ntwo');
  });

  it('reads several events from one chunk, in order', () => {
    const frames = new SseParser().feed('event:a\ndata:1\n\nevent:b\ndata:2\n\n');

    expect(frames.map((frame) => [frame.event, frame.data])).toEqual([
      ['a', '1'],
      ['b', '2'],
    ]);
  });

  it('waits for an event that arrives in pieces — mid-line, mid-field and before the blank line', () => {
    const parser = new SseParser();

    expect(parser.feed('eve')).toEqual([]);
    expect(parser.feed('nt:message.po')).toEqual([]);
    expect(parser.feed('sted\ndata:{"x"')).toEqual([]);
    expect(parser.feed(':1}\n')).toEqual([]);
    expect(parser.feed('\n')).toEqual([{ id: undefined, event: 'message.posted', data: '{"x":1}' }]);
  });

  it('handles CR LF, lone CR and LF line ends alike', () => {
    expect(new SseParser().feed('event:a\r\ndata:1\r\n\r\n')).toHaveLength(1);
    // A lone CR at the very end of the text so far could still be the first half of a CR LF, so it waits for more.
    const lone = new SseParser();
    expect(lone.feed('event:a\rdata:1\r\r')).toEqual([]);
    expect(lone.feed('data:2\n\n').map((frame) => frame.data)).toEqual(['1', '2']);
    expect(new SseParser().feed('event:a\ndata:1\n\n')).toHaveLength(1);
  });

  it('does not mistake a CR LF split across two chunks for two line ends', () => {
    const parser = new SseParser();

    expect(parser.feed('data:1\r')).toEqual([]);
    expect(parser.feed('\n\r\n')).toEqual([{ id: undefined, event: 'message', data: '1' }]);
  });

  it('ignores comments — the heartbeat — and fields it does not know', () => {
    expect(new SseParser().feed(': keepalive\n\nretry: 3000\nunknown: x\n\n')).toEqual([]);
    expect(new SseParser().feed(': keepalive\nevent:a\ndata:1\n\n')[0]?.event).toBe('a');
  });

  it('does not dispatch an event that has no data and no name', () => {
    expect(new SseParser().feed('id:5\n\n')).toEqual([]);
  });

  it('does not carry an id over to the next event', () => {
    const frames = new SseParser().feed('id:1\ndata:a\n\ndata:b\n\n');

    expect(frames.map((frame) => frame.id)).toEqual(['1', undefined]);
  });

  it('keeps an empty data line as an empty string', () => {
    expect(new SseParser().feed('event:x\ndata\n\n')).toEqual([{ id: undefined, event: 'x', data: '' }]);
  });
});
