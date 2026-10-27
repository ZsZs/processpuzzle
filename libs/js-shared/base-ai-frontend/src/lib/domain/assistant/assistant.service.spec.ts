import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AssistantService, AssistantTurnError } from './assistant.service';

const ROOT = 'https://backend/organizations/acme';
const TURNS = `${ROOT}/assistant/turns`;

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

  it('posts the message to the organization\'s turns and reads the session and reply from the events', async () => {
    const turn = service.turn('Define an invoice.');
    const request = http.expectOne(TURNS);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ message: 'Define an invoice.', sessionId: undefined });
    expect(request.request.headers.get('Accept')).toBe('text/event-stream');
    request.flush('event:session\ndata:s-1\n\nevent:reply\ndata:Saved.\n\nevent:done\ndata:\n\n');

    await expect(turn).resolves.toEqual({ sessionId: 's-1', reply: 'Saved.' });
  });

  it('continues a conversation when given its session', async () => {
    const turn = service.turn('Rename it.', 's-1');
    const request = http.expectOne(TURNS);

    expect(request.request.body).toEqual({ message: 'Rename it.', sessionId: 's-1' });
    request.flush('event:session\ndata:s-1\n\nevent:reply\ndata:Renamed.\n\nevent:done\ndata:\n\n');

    await expect(turn).resolves.toEqual({ sessionId: 's-1', reply: 'Renamed.' });
  });

  it('fails a turn whose body reports an error event', async () => {
    const turn = service.turn('Define an invoice.');
    http.expectOne(TURNS).flush('event:session\ndata:s-1\n\nevent:error\ndata:The assistant could not complete this turn.\n\n');

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
