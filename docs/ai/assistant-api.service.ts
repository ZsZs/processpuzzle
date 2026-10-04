import { HttpClient } from '@angular/common/http';
import { Injectable, InjectionToken, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApplyResult, AssistantEvent, DraftYaml, ValidationIssue } from './chat.models';

/** e.g. '/api/ai' */
export const AI_API_BASE_URL = new InjectionToken<string>('AI_API_BASE_URL');

/** Returns the current access token (e.g. from your Keycloak integration). */
export const AI_ACCESS_TOKEN = new InjectionToken<() => Promise<string | undefined>>('AI_ACCESS_TOKEN');

@Injectable({ providedIn: 'root' })
export class AssistantApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(AI_API_BASE_URL);
  private readonly accessToken = inject(AI_ACCESS_TOKEN);

  /**
   * Streams assistant events for one user message.
   * EventSource only supports GET, so we POST with fetch and parse the SSE frames ourselves.
   */
  async *stream(sessionId: string, message: string, signal: AbortSignal): AsyncGenerator<AssistantEvent> {
    const token = await this.accessToken();
    const response = await fetch(`${this.baseUrl}/sessions/${sessionId}/messages`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ message }),
      signal,
    });

    if (!response.ok || !response.body) {
      throw new Error(`Assistant request failed (${response.status})`);
    }

    const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
    let buffer = '';

    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += value;

      // SSE frames are separated by a blank line.
      let separator: number;
      while ((separator = buffer.indexOf('\n\n')) >= 0) {
        const frame = buffer.slice(0, separator);
        buffer = buffer.slice(separator + 2);
        const data = frame
          .split('\n')
          .filter((line) => line.startsWith('data:'))
          .map((line) => line.slice(5).trimStart())
          .join('\n');
        if (data) {
          yield JSON.parse(data) as AssistantEvent;
        }
      }
    }
  }

  /** Replaces the draft on the server (manual edit or undo). Returns validation issues. */
  replaceDraft(sessionId: string, draft: DraftYaml): Promise<ValidationIssue[]> {
    return firstValueFrom(this.http.put<ValidationIssue[]>(`${this.baseUrl}/sessions/${sessionId}/draft`, draft));
  }

  /** Imports the validated draft into the customer's application. */
  apply(sessionId: string): Promise<ApplyResult> {
    return firstValueFrom(this.http.post<ApplyResult>(`${this.baseUrl}/sessions/${sessionId}/apply`, {}));
  }
}
