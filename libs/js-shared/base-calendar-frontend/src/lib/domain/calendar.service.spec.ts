import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { CalendarService, failureKindOf, failureTextOf } from './calendar.service';
import { CalendarEntryInput } from './calendar.types';

const ROOT = 'https://backend/api/v1/organizations/my-org';

const INPUT: CalendarEntryInput = { kind: 'TIMED', title: 'Planning', start: '2026-12-01T09:00:00Z', end: '2026-12-01T10:00:00Z' };

describe('CalendarService', () => {
  let service: CalendarService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: ROOT } } }],
    });
    service = TestBed.inject(CalendarService);
    http = TestBed.inject(HttpTestingController);
  });

  it('gets a calendar by key', async () => {
    const promise = service.getCalendar('team');

    const request = http.expectOne(`${ROOT}/calendar/calendars/team`);
    expect(request.request.method).toBe('GET');
    request.flush({ key: 'team' });
    expect(await promise).toEqual({ key: 'team' });
  });

  it('asks for the occurrences of several calendars in one window', async () => {
    const promise = service.listOccurrences(['team', 'room'], '2026-12-01T00:00:00Z', '2026-12-08T00:00:00Z');

    const request = http.expectOne((candidate) => candidate.url === `${ROOT}/calendar/occurrences`);
    expect(request.request.params.get('calendars')).toBe('team,room');
    expect(request.request.params.get('from')).toBe('2026-12-01T00:00:00Z');
    expect(request.request.params.get('to')).toBe('2026-12-08T00:00:00Z');
    request.flush([]);
    expect(await promise).toEqual([]);
  });

  it('books, reads, replaces and cancels an entry on the calendar it belongs to', async () => {
    const booked = service.bookEntry('team', INPUT);
    const book = http.expectOne(`${ROOT}/calendar/calendars/team/entries`);
    expect(book.request.method).toBe('POST');
    expect(book.request.body).toEqual(INPUT);
    book.flush({ id: 'e-1' });
    await booked;

    const read = service.getEntry('team', 'e-1');
    http.expectOne(`${ROOT}/calendar/calendars/team/entries/e-1`).flush({ id: 'e-1' });
    await read;

    const replaced = service.updateEntry('team', 'e-1', { ...INPUT, version: 2 });
    const put = http.expectOne(`${ROOT}/calendar/calendars/team/entries/e-1`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body.version).toBe(2);
    put.flush({ id: 'e-1' });
    await replaced;

    const cancelled = service.cancelEntry('team', 'e-1');
    const del = http.expectOne(`${ROOT}/calendar/calendars/team/entries/e-1`);
    expect(del.request.method).toBe('DELETE');
    del.flush(null, { status: 204, statusText: 'No Content' });
    await expect(cancelled).resolves.toBeUndefined();
  });

  it('encodes a key that is not path-safe', async () => {
    const promise = service.getCalendar('a/b c');

    http.expectOne(`${ROOT}/calendar/calendars/a%2Fb%20c`).flush({ key: 'a/b c' });
    await expect(promise).resolves.toEqual({ key: 'a/b c' });
  });
});

describe('failureKindOf', () => {
  const error = (status: number, body?: unknown) => new HttpErrorResponse({ status, error: body });

  it('sorts a failed call into what the user is told', () => {
    expect(failureKindOf(error(401))).toBe('forbidden');
    expect(failureKindOf(error(403))).toBe('forbidden');
    expect(failureKindOf(error(404))).toBe('not-found');
    expect(failureKindOf(error(409))).toBe('conflict');
    expect(failureKindOf(error(400))).toBe('invalid');
    expect(failureKindOf(error(422))).toBe('invalid');
    expect(failureKindOf(error(500))).toBe('unavailable');
    expect(failureKindOf(error(0))).toBe('unavailable');
    expect(failureKindOf(new Error('network'))).toBe('unavailable');
  });

  it('passes on the explanation the server gave, when it gave one', () => {
    expect(failureTextOf(error(422, { errorId: 'calendar.booking.invalid', errorText: 'an entry must last at least 30 minutes' }))).toBe('an entry must last at least 30 minutes');
    expect(failureTextOf(error(422, { errorId: 'x' }))).toBeUndefined();
    expect(failureTextOf(error(500, null))).toBeUndefined();
    expect(failureTextOf(new Error('x'))).toBeUndefined();
  });
});
