/** One event of a server-sent event stream. */
export interface SseFrame {
  /** The `id:` field of this event, if it had one. */
  id: string | undefined;
  /** The `event:` field, `message` when the event had none. */
  event: string;
  /** The `data:` lines, joined with a line feed. */
  data: string;
}

/**
 * Reads server-sent events out of text that arrives in arbitrary pieces — a chunk can end in the middle of a line,
 * between a carriage return and its line feed, or after the last field of an event.
 *
 * Written rather than borrowed because the browser's `EventSource` cannot send an `Authorization` header, which
 * is why the stream is read with `fetch`. Follows the field rules of the HTML spec — comments, an optional space
 * after the colon, several `data:` lines — except that an event's `id` is not carried over to the next one: nothing
 * here resumes by `Last-Event-ID`.
 */
export class SseParser {
  private buffer = '';
  private id: string | undefined;
  private event = '';
  private data: string[] = [];
  private hasField = false;

  /** Feeds the next piece of the stream and returns the events it completed. */
  feed(chunk: string): SseFrame[] {
    this.buffer += chunk;
    const frames: SseFrame[] = [];
    for (;;) {
      const end = this.endOfLine();
      if (end === undefined) return frames;
      const line = this.buffer.slice(0, end.start);
      this.buffer = this.buffer.slice(end.next);
      const frame = this.read(line);
      if (frame) frames.push(frame);
    }
  }

  /** Where the first complete line ends and the next begins, or undefined when only part of one has arrived. */
  private endOfLine(): { start: number; next: number } | undefined {
    for (let index = 0; index < this.buffer.length; index++) {
      const character = this.buffer[index];
      if (character === '\n') return { start: index, next: index + 1 };
      if (character === '\r') {
        // A lone CR is a line end, but CR LF is one — wait to see which when the CR is the last character so far.
        if (index === this.buffer.length - 1) return undefined;
        return { start: index, next: this.buffer[index + 1] === '\n' ? index + 2 : index + 1 };
      }
    }
    return undefined;
  }

  private read(line: string): SseFrame | undefined {
    if (line === '') return this.dispatch();
    if (line.startsWith(':')) return undefined;
    const colon = line.indexOf(':');
    const field = colon < 0 ? line : line.slice(0, colon);
    let value = colon < 0 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) value = value.slice(1);
    switch (field) {
      case 'event':
        this.event = value;
        this.hasField = true;
        break;
      case 'data':
        this.data.push(value);
        this.hasField = true;
        break;
      case 'id':
        if (!value.includes('\0')) this.id = value;
        this.hasField = true;
        break;
      default:
        // `retry` and anything unknown are ignored.
        break;
    }
    return undefined;
  }

  private dispatch(): SseFrame | undefined {
    const frame = this.hasField && (this.data.length > 0 || this.event) ? { id: this.id, event: this.event || 'message', data: this.data.join('\n') } : undefined;
    this.id = undefined;
    this.event = '';
    this.data = [];
    this.hasField = false;
    return frame;
  }
}
