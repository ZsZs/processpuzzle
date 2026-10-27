import { HttpClient, HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { lastValueFrom } from 'rxjs';
import { AssistantTurn, parseAssistantEvents } from './assistant-turn';

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

/**
 * Runs one turn of the design assistant against base-ai's turn endpoint.
 *
 * The request goes through the application's `HttpClient`, so the auth interceptor attaches the token. The endpoint
 * answers the whole turn at once as server-sent events, so the body is read as text when it is complete. Token
 * streaming would need a raw `fetch` with the token passed in explicitly; that is a later change.
 */
@Injectable({ providedIn: 'root' })
export class AssistantService {
  private readonly http = inject(HttpClient);
  private readonly root = serviceRootOf(inject(RUNTIME_CONFIGURATION), 'AI_SERVICE_ROOT');

  /**
   * Runs one turn in the organization this root is configured for.
   *
   * @param sessionId the conversation to continue; omitted, the turn opens a new one
   * @throws AssistantTurnError when the turn does not complete
   */
  async turn(message: string, sessionId?: string): Promise<AssistantTurn> {
    const body = await this.post(message, sessionId);
    const events = parseAssistantEvents(body);
    const session = events.find((event) => event.name === 'session');
    const reply = events.find((event) => event.name === 'reply');
    if (!session || !reply || events.some((event) => event.name === 'error')) {
      throw new AssistantTurnError('failed');
    }
    return { sessionId: session.data, reply: reply.data };
  }

  private async post(message: string, sessionId: string | undefined): Promise<string> {
    try {
      return await lastValueFrom(
        this.http.post(`${this.root}/assistant/turns`, { message, sessionId }, {
          headers: { Accept: 'text/event-stream' },
          responseType: 'text',
        }),
      );
    } catch (error) {
      throw new AssistantTurnError(kindOf(error));
    }
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
