import { HttpClient, HttpErrorResponse, HttpParams, HttpStatusCode } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { ChatFailureKind, ChatMessage, ChatSubjectRef, Conversation, PostedMessage, ReadState } from './chat.types';

/** How a failed call is told to the user. */
export function failureKindOf(error: unknown): ChatFailureKind {
  if (!(error instanceof HttpErrorResponse)) return 'unavailable';
  switch (error.status) {
    case HttpStatusCode.Forbidden:
    case HttpStatusCode.Unauthorized:
      return 'forbidden';
    case HttpStatusCode.NotFound:
      return 'not-found';
    case HttpStatusCode.Conflict:
      return 'conflict';
    case HttpStatusCode.BadRequest:
      return 'invalid';
    default:
      return 'unavailable';
  }
}

/**
 * The REST half of chat, against `base-chat-api.yaml`.
 *
 * Goes through the application's `HttpClient`, so its interceptors attach the token and handle an expired session.
 * The organization is part of the configured service root (`CHAT_SERVICE_ROOT`, which falls back to
 * `BACKEND_SERVICE_ROOT`), as for every other feature.
 */
@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);
  private readonly root = serviceRootOf(inject(RUNTIME_CONFIGURATION), 'CHAT_SERVICE_ROOT');
  private readonly base = `${this.root}/chat`;

  /** The conversations of a subject that the caller may read, oldest first. */
  findConversations(subject: ChatSubjectRef, definitionKey?: string): Promise<Conversation[]> {
    let params = new HttpParams().set('subjectType', subject.subjectType).set('subjectId', subject.subjectId);
    if (definitionKey) params = params.set('definition', definitionKey);
    return firstValueFrom(this.http.get<Conversation[]>(`${this.base}/conversations`, { params }));
  }

  openConversation(definitionKey: string, subjectId: string, title?: string): Promise<Conversation> {
    return firstValueFrom(this.http.post<Conversation>(`${this.base}/conversations`, { definitionKey, subjectId, title }));
  }

  /** One page of history, highest `seq` first, below the cursor `before` (exclusive). */
  listMessages(conversationId: string, before?: number, limit?: number): Promise<ChatMessage[]> {
    let params = new HttpParams();
    if (before !== undefined) params = params.set('before', before);
    if (limit !== undefined) params = params.set('limit', limit);
    return firstValueFrom(this.http.get<ChatMessage[]>(`${this.base}/conversations/${conversationId}/messages`, { params }));
  }

  /** Posts a message. Replaying a post with the same `clientMessageId` returns the stored message with `created` false. */
  async postMessage(conversationId: string, clientMessageId: string, body: string): Promise<PostedMessage> {
    const response = await firstValueFrom(
      this.http.post<ChatMessage>(`${this.base}/conversations/${conversationId}/messages`, { clientMessageId, body }, { observe: 'response' }),
    );
    return { message: response.body as ChatMessage, created: response.status === HttpStatusCode.Created };
  }

  markRead(conversationId: string, lastReadSeq: number): Promise<ReadState> {
    return firstValueFrom(this.http.put<ReadState>(`${this.base}/conversations/${conversationId}/read`, { lastReadSeq }));
  }

  async unfollow(conversationId: string): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`${this.base}/conversations/${conversationId}/follow`));
  }

  /** The conversations the caller follows, with their markers. */
  followedConversations(): Promise<Conversation[]> {
    return firstValueFrom(this.http.get<Conversation[]>(`${this.base}/unread`));
  }

  /**
   * The URL of the live stream. `after` says, per conversation, the newest `seq` the client holds, so what it
   * missed while disconnected is replayed.
   */
  streamUrl(after: ReadonlyMap<string, number>): string {
    const pairs = [...after].map(([conversationId, seq]) => `${conversationId}:${seq}`).join(',');
    const query = pairs ? `?after=${encodeURIComponent(pairs)}` : '';
    return `${this.base}/stream${query}`;
  }
}
