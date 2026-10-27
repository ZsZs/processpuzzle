/** The events one turn of the design assistant streams, in the order the server sends them. */
export type AssistantEventName = 'session' | 'reply' | 'done' | 'error';

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

const EVENT_NAMES: readonly string[] = ['session', 'reply', 'done', 'error'];

/**
 * Splits a `text/event-stream` body into its events. Blocks without a known name are skipped: the server only
 * sends the four names above, and anything else is not for the panel.
 */
export function parseAssistantEvents(body: string): AssistantEvent[] {
  return body
    .split(/\r?\n\r?\n/)
    .map(parseBlock)
    .filter((event): event is AssistantEvent => event !== undefined);
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
