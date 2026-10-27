import { HttpClient, HttpDownloadProgressEvent, HttpErrorResponse, HttpEvent, HttpEventType, HttpResponse, HttpStatusCode } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { AssistantTurn, parseAssistantEvents, parseCompleteAssistantEvents } from './assistant-turn';

/** Why a turn did not complete, in terms the panel can say to the designer. */
export type AssistantFailureKind =
  /** The organization has used up its turns or tokens for the day. */
  | 'quota'
  /** The conversation is not this designer's, or has expired after a day without a message. */
  | 'session-not-found'
  /** The caller may not design in this organization. */
  | 'forbidden'
  /** Anything else: the gateway, the model, or the connection. */
  | 'failed';

export class AssistantTurnError extends Error {
  constructor(readonly kind: AssistantFailureKind) {
    super(`The design assistant turn failed: ${kind}`);
    this.name = 'AssistantTurnError';
  }
}

/** Receives each piece of the answer as the model produces it. */
export type AssistantDeltaListener = (text: string) => void;

/**
 * Runs one turn of the design assistant against base-ai's turn endpoint.
 *
 * The request goes through the application's `HttpClient`, so the auth interceptor attaches the token, and the
 * answer is read as it arrives: `HttpClient` reports the body so far on each download-progress event, and the
 * `delta` events complete in it are handed to the listener. The whole body, once complete, is what the turn is read
 * from.
 */
@Injectable({ providedIn: 'root' })
export class AssistantService {
  private readonly http = inject(HttpClient);
  private readonly root = serviceRootOf(inject(RUNTIME_CONFIGURATION), 'AI_SERVICE_ROOT');

  /**
   * Runs one turn in the organization this root is configured for.
   *
   * @param sessionId the conversation to continue; omitted, the turn opens a new one
   * @param onDelta   told each piece of the answer as it arrives; the pieces concatenate to the reply
   * @throws AssistantTurnError when the turn does not complete
   */
  async turn(message: string, sessionId?: string, onDelta?: AssistantDeltaListener): Promise<AssistantTurn> {
    const body = await this.post(message, sessionId, onDelta);
    const events = parseAssistantEvents(body);
    const session = events.find((event) => event.name === 'session');
    const reply = events.find((event) => event.name === 'reply');
    if (!session || !reply || events.some((event) => event.name === 'error')) {
      throw new AssistantTurnError('failed');
    }
    return { sessionId: session.data, reply: reply.data };
  }

  private post(message: string, sessionId: string | undefined, onDelta: AssistantDeltaListener | undefined): Promise<string> {
    const deltas = new DeltaRelay(onDelta);
    return new Promise((resolve, reject) => {
      this.http
        .post(`${this.root}/assistant/turns`, { message, sessionId }, {
          headers: { Accept: 'text/event-stream' },
          observe: 'events',
          reportProgress: true,
          responseType: 'text',
        })
        .subscribe({
          next: (event: HttpEvent<string>) => {
            if (event.type === HttpEventType.DownloadProgress) {
              deltas.relay((event as HttpDownloadProgressEvent).partialText ?? '');
            } else if (event.type === HttpEventType.Response) {
              const body = (event as HttpResponse<string>).body ?? '';
              deltas.relay(body);
              resolve(body);
            }
          },
          error: (error: unknown) => reject(new AssistantTurnError(kindOf(error))),
        });
    });
  }
}

/** Hands each `delta` of a growing body to the listener exactly once, however the body is split across events. */
class DeltaRelay {
  private delivered = 0;

  constructor(private readonly listener: AssistantDeltaListener | undefined) {}

  relay(bodySoFar: string): void {
    if (!this.listener) return;
    const deltas = parseCompleteAssistantEvents(bodySoFar).filter((event) => event.name === 'delta');
    for (const delta of deltas.slice(this.delivered)) this.listener(delta.data);
    this.delivered = Math.max(this.delivered, deltas.length);
  }
}

function kindOf(error: unknown): AssistantFailureKind {
  if (!(error instanceof HttpErrorResponse)) return 'failed';
  switch (error.status) {
    case HttpStatusCode.TooManyRequests:
      return 'quota';
    case HttpStatusCode.NotFound:
      return 'session-not-found';
    case HttpStatusCode.Forbidden:
      return 'forbidden';
    default:
      return 'failed';
  }
}
