import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ChatService, failureKindOf } from './chat.service';
import { ChatMessage, Conversation } from './chat.types';

const ROOT = 'https://backend/api/v1/organizations/my-org';

const MESSAGE: ChatMessage = { seq: 3, clientMessageId: 'c-1', authorId: 'u-1', kind: 'TEXT', body: 'Hello', createdAt: '2026-11-16T10:00:00Z' };

function configure(configuration: object = { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: ROOT } }) {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: configuration }],
  });
  return { service: TestBed.inject(ChatService), http: TestBed.inject(HttpTestingController) };
}

describe('ChatService', () => {
  let service: ChatService;
  let http: HttpTestingController;

  beforeEach(() => {
    ({ service, http } = configure());
  });

  it('finds the conversations of a subject, narrowed by definition when asked', async () => {
    const promise = service.findConversations({ subjectType: 'ENTITY', subjectId: 'o-1' }, 'order-discussion');

    const request = http.expectOne((candidate) => candidate.url === `${ROOT}/chat/conversations`);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('subjectType')).toBe('ENTITY');
    expect(request.request.params.get('subjectId')).toBe('o-1');
    expect(request.request.params.get('definition')).toBe('order-discussion');
    request.flush([{ id: 'c-1' }]);
    expect(await promise).toEqual([{ id: 'c-1' }]);
  });

  it('leaves the definition out of the query when none is given', async () => {
    const promise = service.findConversations({ subjectType: 'ENTITY', subjectId: 'o-1' });

    const request = http.expectOne((candidate) => candidate.url === `${ROOT}/chat/conversations`);
    expect(request.request.params.has('definition')).toBe(false);
    request.flush([]);
    await promise;
  });

  it('opens a conversation with the definition, the subject and the title', async () => {
    const promise = service.openConversation('race-protests', 'r-1', 'Protest of race 3');

    const request = http.expectOne(`${ROOT}/chat/conversations`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ definitionKey: 'race-protests', subjectId: 'r-1', title: 'Protest of race 3' });
    request.flush({ id: 'c-1' } as Conversation);
    expect((await promise).id).toBe('c-1');
  });

  it('pages history by cursor and limit', async () => {
    const promise = service.listMessages('c-1', 40, 25);

    const request = http.expectOne((candidate) => candidate.url === `${ROOT}/chat/conversations/c-1/messages`);
    expect(request.request.params.get('before')).toBe('40');
    expect(request.request.params.get('limit')).toBe('25');
    request.flush([MESSAGE]);
    expect(await promise).toEqual([MESSAGE]);
  });

  it('asks for the newest page when there is no cursor', async () => {
    const promise = service.listMessages('c-1');

    const request = http.expectOne((candidate) => candidate.url === `${ROOT}/chat/conversations/c-1/messages`);
    expect(request.request.params.keys()).toEqual([]);
    request.flush([]);
    await promise;
  });

  it('tells a new post (201) from a replay (200)', async () => {
    const created = service.postMessage('c-1', 'c-1', 'Hello');
    const first = http.expectOne(`${ROOT}/chat/conversations/c-1/messages`);
    expect(first.request.method).toBe('POST');
    expect(first.request.body).toEqual({ clientMessageId: 'c-1', body: 'Hello' });
    first.flush(MESSAGE, { status: 201, statusText: 'Created' });
    expect(await created).toEqual({ message: MESSAGE, created: true });

    const replayed = service.postMessage('c-1', 'c-1', 'Hello');
    http.expectOne(`${ROOT}/chat/conversations/c-1/messages`).flush(MESSAGE, { status: 200, statusText: 'OK' });
    expect(await replayed).toEqual({ message: MESSAGE, created: false });
  });

  it('marks a conversation read', async () => {
    const promise = service.markRead('c-1', 7);

    const request = http.expectOne(`${ROOT}/chat/conversations/c-1/read`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ lastReadSeq: 7 });
    request.flush({ conversationId: 'c-1', lastReadSeq: 7, lastSeq: 9, unread: 2 });
    expect((await promise).unread).toBe(2);
  });

  it('unfollows and lists what is followed', async () => {
    const unfollowed = service.unfollow('c-1');
    const request = http.expectOne(`${ROOT}/chat/conversations/c-1/follow`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await unfollowed;

    const followed = service.followedConversations();
    http.expectOne(`${ROOT}/chat/unread`).flush([{ id: 'c-2' }]);
    expect(await followed).toEqual([{ id: 'c-2' }]);
  });

  it('builds the stream URL, saying what the client already holds', () => {
    expect(service.streamUrl(new Map())).toBe(`${ROOT}/chat/stream`);
    expect(service.streamUrl(new Map([['c-1', 4]]))).toBe(`${ROOT}/chat/stream?after=${encodeURIComponent('c-1:4')}`);
    expect(service.streamUrl(new Map([['c-1', 4], ['c-2', 0]]))).toBe(`${ROOT}/chat/stream?after=${encodeURIComponent('c-1:4,c-2:0')}`);
  });

  it('prefers CHAT_SERVICE_ROOT over the backend root', () => {
    TestBed.resetTestingModule();
    const { service: specific } = configure({ BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: 'https://other', CHAT_SERVICE_ROOT: 'https://chat/org' } });

    expect(specific.streamUrl(new Map())).toBe('https://chat/org/chat/stream');
  });
});

describe('failureKindOf', () => {
  const failed = (status: number) => new HttpErrorResponse({ status });

  it.each([
    [401, 'forbidden'],
    [403, 'forbidden'],
    [404, 'not-found'],
    [409, 'conflict'],
    [400, 'invalid'],
    [500, 'unavailable'],
    [0, 'unavailable'],
  ])('reads %i as %s', (status, kind) => {
    expect(failureKindOf(failed(status))).toBe(kind);
  });

  it('reads anything that is not an HTTP failure as unavailable', () => {
    expect(failureKindOf(new Error('boom'))).toBe('unavailable');
  });
});
