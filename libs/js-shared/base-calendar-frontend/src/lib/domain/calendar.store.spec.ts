import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CalendarService } from './calendar.service';
import { CalendarStore, KindChangeError } from './calendar.store';
import { CalendarEntry, CalendarInfo } from './calendar.types';

function calendar(key: string, canBook = true): CalendarInfo {
  return { key, name: key, kind: 'TEAM', effectiveTimeZone: 'Europe/Budapest', permissions: { canBook, canManage: false } };
}

const TIMED: CalendarEntry = {
  id: 'e-1',
  calendarKey: 'team',
  kind: 'TIMED',
  busy: false,
  title: 'Planning',
  description: 'Quarterly',
  location: 'Room 1',
  category: 'meeting',
  visibility: 'PRIVATE',
  start: '2026-12-01T09:00:00Z',
  end: '2026-12-01T10:00:00Z',
  timeZone: 'Europe/Budapest',
  version: 4,
};

describe('CalendarStore', () => {
  let service: { [K in keyof CalendarService]: ReturnType<typeof vi.fn> };
  let store: CalendarStore;

  beforeEach(() => {
    service = {
      getCalendar: vi.fn(),
      listOccurrences: vi.fn().mockResolvedValue([]),
      getEntry: vi.fn(),
      bookEntry: vi.fn(),
      updateEntry: vi.fn().mockResolvedValue(TIMED),
      cancelEntry: vi.fn(),
      listMyOccurrences: vi.fn().mockResolvedValue([]),
      searchDirectory: vi.fn().mockResolvedValue([]),
      roleCatalog: vi.fn().mockResolvedValue([]),
      answerInvitation: vi.fn(),
    } as never;
    TestBed.configureTestingModule({ providers: [CalendarStore, { provide: CalendarService, useValue: service }] });
    store = TestBed.inject(CalendarStore);
  });

  describe('loadCalendars', () => {
    it('loads the calendars in the order asked and derives the organization zone and booking right', async () => {
      service.getCalendar.mockImplementation((key: string) => Promise.resolve(calendar(key, key === 'room')));

      await store.loadCalendars(['team', 'room']);

      expect(store.calendars().map((c) => c.key)).toEqual(['team', 'room']);
      expect(store.status()).toBe('ready');
      expect(store.timeZone()).toBe('Europe/Budapest');
      expect(store.canBook()).toBe(true);
      expect(store.calendarOf('room')?.key).toBe('room');
    });

    it('leaves out a calendar the caller may not read and is still ready', async () => {
      service.getCalendar.mockImplementation((key: string) => (key === 'secret' ? Promise.reject(new HttpErrorResponse({ status: 404 })) : Promise.resolve(calendar(key))));

      await store.loadCalendars(['secret', 'team']);

      expect(store.calendars().map((c) => c.key)).toEqual(['team']);
      expect(store.status()).toBe('ready');
      expect(store.failure()).toBeNull();
    });

    it('is an error, with the reason, only when no calendar can be shown', async () => {
      service.getCalendar.mockRejectedValue(new HttpErrorResponse({ status: 404 }));

      await store.loadCalendars(['secret']);

      expect(store.calendars()).toEqual([]);
      expect(store.status()).toBe('error');
      expect(store.failure()).toBe('not-found');
    });

    it('reports an outage as unavailable', async () => {
      service.getCalendar.mockRejectedValue(new HttpErrorResponse({ status: 503 }));

      await store.loadCalendars(['team']);

      expect(store.failure()).toBe('unavailable');
    });

    it('asks for each key once and clears everything for no keys', async () => {
      service.getCalendar.mockImplementation((key: string) => Promise.resolve(calendar(key)));
      await store.loadCalendars(['team', 'team']);
      expect(service.getCalendar).toHaveBeenCalledTimes(1);

      await store.loadCalendars([]);

      expect(store.calendars()).toEqual([]);
      expect(store.status()).toBe('idle');
      expect(store.timeZone()).toBe('UTC');
      expect(store.canBook()).toBe(false);
    });
  });

  describe('loadOccurrences', () => {
    it('asks for the shown calendars only', async () => {
      service.getCalendar.mockImplementation((key: string) => Promise.resolve(calendar(key)));
      await store.loadCalendars(['team', 'room']);

      await store.loadOccurrences('2026-12-01T00:00:00Z', '2026-12-08T00:00:00Z');

      expect(service.listOccurrences).toHaveBeenCalledWith(['team', 'room'], '2026-12-01T00:00:00Z', '2026-12-08T00:00:00Z');
    });

    it('does not ask for the caller own occurrences unless told to', async () => {
      service.getCalendar.mockImplementation((key: string) => Promise.resolve(calendar(key)));
      await store.loadCalendars(['team']);

      await store.loadOccurrences('2026-12-01T00:00:00Z', '2026-12-08T00:00:00Z');

      expect(service.listMyOccurrences).not.toHaveBeenCalled();
    });

    it('adds the caller own occurrences, once each, when asked to', async () => {
      service.getCalendar.mockImplementation((key: string) => Promise.resolve(calendar(key)));
      await store.loadCalendars(['team']);
      const shared = { entryId: 'e-1', calendarKey: 'team', allDay: false, busy: false, title: 'Planning' };
      const other = { entryId: 'e-9', calendarKey: 'room', allDay: false, busy: false, title: 'Invited', invited: true };
      service.listOccurrences.mockResolvedValue([shared]);
      service.listMyOccurrences.mockResolvedValue([{ ...shared, mine: true }, other]);

      const result = await store.loadOccurrences('2026-12-01T00:00:00Z', '2026-12-08T00:00:00Z', true);

      expect(service.listMyOccurrences).toHaveBeenCalledWith('2026-12-01T00:00:00Z', '2026-12-08T00:00:00Z');
      expect(result).toEqual([shared, other]);
    });

    it('still shows the caller own occurrences when no calendar is shown', async () => {
      service.listMyOccurrences.mockResolvedValue([{ entryId: 'e-9', calendarKey: 'room', allDay: false, busy: false }]);

      const result = await store.loadOccurrences('2026-12-01T00:00:00Z', '2026-12-08T00:00:00Z', true);

      expect(result).toHaveLength(1);
      expect(service.listOccurrences).not.toHaveBeenCalled();
    });

    it('asks nothing when no calendar is shown', async () => {
      expect(await store.loadOccurrences('2026-12-01T00:00:00Z', '2026-12-08T00:00:00Z')).toEqual([]);
      expect(service.listOccurrences).not.toHaveBeenCalled();
    });
  });

  describe('reschedule', () => {
    it('writes the entry back whole with the new times and the version it read, so no field is lost', async () => {
      service.getEntry.mockResolvedValue(TIMED);

      await store.reschedule('team', 'e-1', { allDay: false, start: '2026-12-02T09:00:00Z', end: '2026-12-02T11:00:00Z' });

      expect(service.updateEntry).toHaveBeenCalledWith('team', 'e-1', {
        kind: 'TIMED',
        title: 'Planning',
        description: 'Quarterly',
        location: 'Room 1',
        category: 'meeting',
        visibility: 'PRIVATE',
        version: 4,
        start: '2026-12-02T09:00:00Z',
        end: '2026-12-02T11:00:00Z',
        timeZone: 'Europe/Budapest',
      });
    });

    it('moves an all-day entry by its dates', async () => {
      service.getEntry.mockResolvedValue({ ...TIMED, kind: 'ALL_DAY', start: null, end: null, timeZone: null, startDate: '2026-12-24', endDate: '2026-12-25' });

      await store.reschedule('team', 'e-1', { allDay: true, startDate: '2026-12-26', endDate: '2026-12-28' });

      expect(service.updateEntry).toHaveBeenCalledWith('team', 'e-1', expect.objectContaining({ kind: 'ALL_DAY', startDate: '2026-12-26', endDate: '2026-12-28' }));
      expect(service.updateEntry.mock.calls[0][2]).not.toHaveProperty('start');
    });

    it('refuses to turn a timed entry into an all-day one, or back, and writes nothing', async () => {
      service.getEntry.mockResolvedValue(TIMED);
      await expect(store.reschedule('team', 'e-1', { allDay: true, startDate: '2026-12-02', endDate: '2026-12-03' })).rejects.toBeInstanceOf(KindChangeError);

      service.getEntry.mockResolvedValue({ ...TIMED, kind: 'ALL_DAY' });
      await expect(store.reschedule('team', 'e-1', { allDay: false, start: 'x', end: 'y' })).rejects.toBeInstanceOf(KindChangeError);

      expect(service.updateEntry).not.toHaveBeenCalled();
    });

    it('lets a refusal of the update through for the caller to revert the move', async () => {
      service.getEntry.mockResolvedValue(TIMED);
      service.updateEntry.mockRejectedValue(new HttpErrorResponse({ status: 409 }));

      await expect(store.reschedule('team', 'e-1', { allDay: false, start: 'a', end: 'b' })).rejects.toBeInstanceOf(HttpErrorResponse);
    });
  });

  it('answers an invitation through the service and hands back the refreshed entry', async () => {
    service.answerInvitation.mockResolvedValue({ ...TIMED, canRespond: true });

    const entry = await store.answer('team', 'e-1', 'ACCEPTED');

    expect(service.answerInvitation).toHaveBeenCalledWith('team', 'e-1', 'ACCEPTED');
    expect(entry.canRespond).toBe(true);
  });

  it('passes the directory search and the role catalog through to the service', async () => {
    service.searchDirectory.mockResolvedValue([{ sub: 's-1', displayName: 'Ann' }]);
    service.roleCatalog.mockResolvedValue([{ name: 'team-lead' }]);

    expect(await store.searchDirectory('an')).toEqual([{ sub: 's-1', displayName: 'Ann' }]);
    expect(await store.roleCatalog()).toEqual([{ name: 'team-lead' }]);
    expect(service.searchDirectory).toHaveBeenCalledWith('an');
  });

  it('passes the entry calls through to the service', async () => {
    service.bookEntry.mockResolvedValue(TIMED);
    service.getEntry.mockResolvedValue(TIMED);

    await store.book('team', { kind: 'TIMED', title: 'x' });
    await store.update('team', 'e-1', { kind: 'TIMED', title: 'y' });
    await store.cancel('team', 'e-1');
    await store.getEntry('team', 'e-1');

    expect(service.bookEntry).toHaveBeenCalledWith('team', { kind: 'TIMED', title: 'x' });
    expect(service.updateEntry).toHaveBeenCalledWith('team', 'e-1', { kind: 'TIMED', title: 'y' });
    expect(service.cancelEntry).toHaveBeenCalledWith('team', 'e-1');
    expect(service.getEntry).toHaveBeenCalledWith('team', 'e-1');
  });
});
