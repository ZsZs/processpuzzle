import { HttpEventType, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AssistantService, AssistantTurnError } from './assistant.service';

const ROOT = 'https://backend/organizations/acme';
const TURNS = `${ROOT}/assistant/turns`;
const SESSION = 'event:session\ndata:s-1\n\n';
const DONE = 'event:done\ndata:\n\n';

describe('AssistantService', () => {
  let service: AssistantService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: ROOT } } },
      ],
    });
    service = TestBed.inject(AssistantService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it("posts the message to the organization's turns and reads the session and reply from the events", async () => {
    const turn = service.turn('Define an invoice.');
    const request = http.expectOne(TURNS);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ message: 'Define an invoice.', sessionId: undefined });
    expect(request.request.headers.get('Accept')).toBe('text/event-stream');
    expect(request.request.reportProgress).toBe(true);
    request.flush(`${SESSION}event:reply\ndata:Saved.\n\n${DONE}`);

    await expect(turn).resolves.toEqual({ sessionId: 's-1', reply: 'Saved.' });
  });

  it('continues a conversation when given its session', async () => {
    const turn = service.turn('Rename it.', 's-1');
    const request = http.expectOne(TURNS);

    expect(request.request.body).toEqual({ message: 'Rename it.', sessionId: 's-1' });
    request.flush(`${SESSION}event:reply\ndata:Renamed.\n\n${DONE}`);

    await expect(turn).resolves.toEqual({ sessionId: 's-1', reply: 'Renamed.' });
  });

  it('hands each piece of the answer on once, as the body arrives, however the arrival splits it', async () => {
    const onDelta = vi.fn();
    const turn = service.turn('Define an invoice.', undefined, onDelta);
    const request = http.expectOne(TURNS);

    // The first progress event ends mid-way through a delta's data line: that piece must wait for its blank line.
    const first = `${SESSION}event:delta\ndata:Saved \n\nevent:delta\ndata:the inv`;
    request.event({ type: HttpEventType.DownloadProgress, loaded: first.length, partialText: first });
    expect(onDelta.mock.calls).toEqual([['Saved ']]);

    const second = `${first}oice \n\nevent:delta\ndata:draft.\n\n`;
    request.event({ type: HttpEventType.DownloadProgress, loaded: second.length, partialText: second });
    expect(onDelta.mock.calls).toEqual([['Saved '], ['the invoice '], ['draft.']]);

    request.flush(`${second}event:reply\ndata:Saved the invoice draft.\n\n${DONE}`);

    await expect(turn).resolves.toEqual({ sessionId: 's-1', reply: 'Saved the invoice draft.' });
    expect(onDelta).toHaveBeenCalledTimes(3);
  });

  it('still hands the pieces on when the whole body arrives at once', async () => {
    const onDelta = vi.fn();
    const turn = service.turn('Define an invoice.', undefined, onDelta);
    http.expectOne(TURNS).flush(`${SESSION}event:delta\ndata:Saved.\n\nevent:reply\ndata:Saved.\n\n${DONE}`);

    await expect(turn).resolves.toEqual({ sessionId: 's-1', reply: 'Saved.' });
    expect(onDelta.mock.calls).toEqual([['Saved.']]);
  });

  it('fails a turn whose body reports an error event', async () => {
    const turn = service.turn('Define an invoice.');
    http.expectOne(TURNS).flush(`${SESSION}event:error\ndata:The assistant could not complete this turn.\n\n`);

    await expect(turn).rejects.toMatchObject({ kind: 'failed' });
  });

  it.each([
    [429, 'quota'],
    [404, 'session-not-found'],
    [403, 'forbidden'],
    [500, 'failed'],
  ] as const)('maps HTTP %i to the failure %s', async (status, kind) => {
    const turn = service.turn('Define an invoice.');
    http.expectOne(TURNS).flush({ message: 'refused' }, { status, statusText: 'refused' });

    const error = await turn.catch((caught: unknown) => caught);
    expect(error).toBeInstanceOf(AssistantTurnError);
    expect((error as AssistantTurnError).kind).toBe(kind);
  });
});
