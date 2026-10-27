/**
 * The events one turn of the design assistant streams, in the order the server sends them: the conversation, then
 * the pieces of the answer as the model produces them, then the whole answer, then `done`; or `error`.
 */
export type AssistantEventName = 'session' | 'delta' | 'reply' | 'done' | 'error';

/** One server-sent event: its name and its data, with multi-line data joined by newlines as the SSE format says. */
export interface AssistantEvent {
  name: AssistantEventName;
  data: string;
}

/** The turn as the panel shows it: the conversation it belongs to, and what the assistant answered. */
export interface AssistantTurn {
  sessionId: string;
  reply: string;
}

const EVENT_NAMES: readonly string[] = ['session', 'delta', 'reply', 'done', 'error'];

/**
 * Splits a `text/event-stream` body into its events. Blocks without a known name are skipped: the server only
 * sends the names above, and anything else is not for the panel.
 */
export function parseAssistantEvents(body: string): AssistantEvent[] {
  return body
    .split(/\r?\n\r?\n/)
    .map(parseBlock)
    .filter((event): event is AssistantEvent => event !== undefined);
}

/**
 * The events of a body that is still arriving: only its complete blocks, so a `data:` line cut mid-way is not read as
 * a shorter piece. A block is complete once the blank line that ends it has arrived.
 */
export function parseCompleteAssistantEvents(partialBody: string): AssistantEvent[] {
  const end = partialBody.search(/\r?\n\r?\n(?![\s\S]*\r?\n\r?\n)/);
  if (end < 0) return [];
  const match = /\r?\n\r?\n/.exec(partialBody.slice(end));
  return parseAssistantEvents(partialBody.slice(0, end + (match?.[0].length ?? 0)));
}

function parseBlock(block: string): AssistantEvent | undefined {
  let name: string | undefined;
  const data: string[] = [];
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) {
      name = line.slice('event:'.length).trim();
    } else if (line.startsWith('data:')) {
      data.push(line.slice('data:'.length).replace(/^ /, ''));
    }
  }
  return name !== undefined && EVENT_NAMES.includes(name) ? { name: name as AssistantEventName, data: data.join('\n') } : undefined;
}
