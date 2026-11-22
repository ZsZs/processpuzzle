import { HttpErrorResponse } from '@angular/common/http';
import { Component, input, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { By } from '@angular/platform-browser';
import { CalendarOptions, FullCalendarComponent, FullCalendarModule } from '@fullcalendar/angular';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CalendarStore, KindChangeError } from '../../domain/calendar.store';
import { CalendarEntry, CalendarInfo, Occurrence } from '../../domain/calendar.types';
import { calendarTranslations } from '../../test-translations';
import { EntryDialogComponent } from '../entry-dialog/entry-dialog.component';
import { CalendarComponent } from './calendar.component';

/** FullCalendar draws a lot of DOM that tells nothing about this widget; the stub keeps what the widget hands it. */
// The stub has to answer to the real tag, which carries FullCalendar's prefix, not ours; a constant rather than a
// literal, so the prefix rule — which is about the selectors of our own components — has nothing to say about it.
const FULL_CALENDAR_TAG = 'full-calendar';

@Component({ selector: FULL_CALENDAR_TAG, standalone: true, template: '', providers: [{ provide: FullCalendarComponent, useExisting: FullCalendarStub }] })
class FullCalendarStub {
  readonly options = input<CalendarOptions>();
  readonly api = { unselect: vi.fn(), refetchEvents: vi.fn() };
  getApi() {
    return this.api;
  }
}

function calendar(key: string, canBook = true, overrides: Partial<CalendarInfo> = {}): CalendarInfo {
  return { key, name: `Calendar ${key}`, kind: 'TEAM', effectiveTimeZone: 'Europe/Budapest', permissions: { canBook, canManage: false }, ...overrides };
}

function fakeStore() {
  return {
    calendars: signal<readonly CalendarInfo[]>([]),
    status: signal<'idle' | 'loading' | 'ready' | 'error'>('idle'),
    failure: signal<'forbidden' | 'not-found' | 'conflict' | 'invalid' | 'unavailable' | null>(null),
    timeZone: signal('Europe/Budapest'),
    canBook: signal(true),
    loadCalendars: vi.fn(async (_keys: readonly string[]): Promise<void> => undefined),
    loadOccurrences: vi.fn(async (_from: string, _to: string, _includeMine?: boolean): Promise<Occurrence[]> => []),
    getEntry: vi.fn(async (_calendarKey: string, _entryId: string): Promise<CalendarEntry> => ENTRY),
    reschedule: vi.fn(async (): Promise<CalendarEntry> => ENTRY),
  };
}
type FakeStore = ReturnType<typeof fakeStore>;

const ENTRY: CalendarEntry = { id: 'e-1', calendarKey: 'team', kind: 'TIMED', busy: false, title: 'Planning', canEdit: true, version: 1 };

describe('CalendarComponent', () => {
  let store: FakeStore;
  let dialog: { open: ReturnType<typeof vi.fn> };
  let fixture: ComponentFixture<CalendarComponent>;

  /** Puts the store in the state of a loaded calendar and renders. */
  function show(calendars: CalendarInfo[] = [calendar('team')], setup?: (component: CalendarComponent) => void): void {
    store.calendars.set(calendars);
    store.status.set('ready');
    store.canBook.set(calendars.some((c) => c.permissions.canBook));
    fixture.componentRef.setInput('calendarKeys', calendars.map((c) => c.key));
    setup?.(fixture.componentInstance);
    fixture.detectChanges();
  }

  const stub = () => fixture.debugElement.query(By.directive(FullCalendarStub)).componentInstance as FullCalendarStub;
  const options = () => stub().options() as CalendarOptions;

  beforeEach(() => {
    store = fakeStore();
    dialog = { open: vi.fn().mockReturnValue({ afterClosed: () => of(undefined) }) };
    TestBed.configureTestingModule({
      imports: [CalendarComponent],
      providers: [provideTranslocoTesting({ translations: calendarTranslations() }), { provide: MatDialog, useValue: dialog }],
    });
    TestBed.overrideComponent(CalendarComponent, {
      remove: { imports: [FullCalendarModule], providers: [CalendarStore] },
      add: { imports: [FullCalendarStub], providers: [{ provide: CalendarStore, useValue: store }] },
    });
    fixture = TestBed.createComponent(CalendarComponent);
  });

  describe('states', () => {
    it('says so when no calendar is chosen', () => {
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="calendar-none"]').textContent).toContain('No calendar selected.');
      expect(fixture.debugElement.query(By.directive(FullCalendarStub))).toBeNull();
    });

    it('says it is loading while the calendars load', () => {
      store.status.set('loading');
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="calendar-loading"]')).not.toBeNull();
    });

    it('tells an unreadable calendar from an outage', () => {
      store.status.set('error');
      store.failure.set('not-found');
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="calendar-error"]').textContent).toContain('not available');

      store.failure.set('unavailable');
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="calendar-error"]').textContent).toContain('could not be reached');
    });

    it('loads the calendars it is given, and again when they change', () => {
      fixture.componentRef.setInput('calendarKeys', ['team', 'room']);
      fixture.detectChanges();
      expect(store.loadCalendars).toHaveBeenLastCalledWith(['team', 'room']);

      fixture.componentRef.setInput('calendarKeys', ['team']);
      fixture.detectChanges();
      expect(store.loadCalendars).toHaveBeenLastCalledWith(['team']);
    });
  });

  describe('options', () => {
    it('draws the week in the organization zone by default, with all four views to switch between', () => {
      show();

      expect(options()).toMatchObject({
        initialView: 'timeGridWeek',
        timeZone: 'Europe/Budapest',
        firstDay: 1,
        slotMinTime: '07:00',
        slotMaxTime: '20:00',
        height: 'auto',
        locale: 'en',
        headerToolbar: { start: 'prev,next today', center: 'title', end: 'timeGridDay,timeGridWeek,dayGridMonth,listWeek' },
      });
    });

    it('takes the designers choices', () => {
      show([calendar('team')], () => {
        fixture.componentRef.setInput('defaultView', 'month');
        fixture.componentRef.setInput('views', ['month', 'list']);
        fixture.componentRef.setInput('firstDayOfWeek', 0);
        fixture.componentRef.setInput('slotMinTime', '06:00');
        fixture.componentRef.setInput('slotMaxTime', '22:00');
        fixture.componentRef.setInput('height', '640px');
      });

      expect(options()).toMatchObject({
        initialView: 'dayGridMonth',
        firstDay: 0,
        slotMinTime: '06:00',
        slotMaxTime: '22:00',
        height: '640px',
        headerToolbar: { end: 'dayGridMonth,listWeek' },
      });
    });

    it('starts on the first offered view when the chosen one is not offered or does not exist', () => {
      show([calendar('team')], () => {
        fixture.componentRef.setInput('defaultView', 'week');
        fixture.componentRef.setInput('views', ['list', 'month']);
      });
      expect(options().initialView).toBe('listWeek');

      fixture.componentRef.setInput('defaultView', 'fortnight');
      fixture.detectChanges();
      expect(options().initialView).toBe('listWeek');
    });

    it('offers all views when the names given are none it knows', () => {
      show([calendar('team')], () => fixture.componentRef.setInput('views', ['decade']));

      expect(options().headerToolbar).toMatchObject({ end: 'timeGridDay,timeGridWeek,dayGridMonth,listWeek' });
    });

    it('lets a caller who may book select, drag and resize', () => {
      show();

      expect(options()).toMatchObject({ selectable: true, editable: true });
    });

    it('is read-only for a caller who may not book, whatever the designer says', () => {
      show([calendar('team', false)]);

      expect(options()).toMatchObject({ selectable: false, editable: false });
    });

    it('is read-only when the designer says so', () => {
      show([calendar('team')], () => fixture.componentRef.setInput('readOnly', true));

      expect(options()).toMatchObject({ selectable: false, editable: false });
    });
  });

  describe('events', () => {
    it('asks for the window in instants and maps what comes back, coloring by calendar and hiding busy details', async () => {
      store.loadOccurrences.mockResolvedValue([
        { entryId: 'e-1', calendarKey: 'team', allDay: false, busy: false, title: 'Planning', start: '2026-12-01T09:00:00Z', end: '2026-12-01T10:00:00Z', canEdit: true },
        { entryId: 'e-2', calendarKey: 'room', allDay: false, busy: true, start: '2026-12-01T11:00:00Z', end: '2026-12-01T12:00:00Z' },
      ]);
      show([calendar('team'), calendar('room')]);

      const events = await (options().events as (info: unknown) => Promise<Array<Record<string, unknown>>>)({
        startStr: '2026-11-30T00:00:00+01:00',
        endStr: '2026-12-07T00:00:00+01:00',
      });

      expect(store.loadOccurrences).toHaveBeenCalledWith('2026-11-29T23:00:00Z', '2026-12-06T23:00:00Z', false);
      expect(events).toHaveLength(2);
      expect(events[0]).toMatchObject({ title: 'Planning', color: 'var(--pp-calendar-color-0)', editable: true });
      expect(events[1]).toMatchObject({ title: 'Busy', color: 'var(--pp-calendar-color-1)', editable: false });
    });
  });

  describe('includeMine', () => {
    it('is passed to the store when the window is loaded', async () => {
      show([calendar('team')], () => fixture.componentRef.setInput('includeMine', true));

      await (options().events as (info: unknown) => Promise<unknown>)({ startStr: '2026-11-30T00:00:00+01:00', endStr: '2026-12-07T00:00:00+01:00' });

      expect(store.loadOccurrences).toHaveBeenCalledWith('2026-11-29T23:00:00Z', '2026-12-06T23:00:00Z', true);
    });
  });

  describe('booking', () => {
    const slot = { startStr: '2026-12-01T10:00:00+01:00', endStr: '2026-12-01T11:00:00+01:00', allDay: false };

    it('opens the dialog for a dragged slot, offering only the calendars the caller may book on', () => {
      show([calendar('team'), calendar('news', false)]);
      const emitted: unknown[] = [];
      fixture.componentInstance.slotSelected.subscribe((value) => emitted.push(value));

      (options().select as (info: unknown) => void)(slot);

      expect(emitted).toEqual([{ start: slot.startStr, end: slot.endStr, allDay: false }]);
      expect(stub().api.unselect).toHaveBeenCalled();
      expect(dialog.open).toHaveBeenCalledTimes(1);
      const [component, config] = dialog.open.mock.calls[0];
      expect(component).toBe(EntryDialogComponent);
      expect(config.data.mode).toBe('create');
      expect(config.data.slot).toEqual({ start: slot.startStr, end: slot.endStr, allDay: false });
      expect(config.data.calendars.map((c: CalendarInfo) => c.key)).toEqual(['team']);
    });

    it('opens nothing when the calendar is read-only', () => {
      show([calendar('team')], () => fixture.componentRef.setInput('readOnly', true));

      (options().select as (info: unknown) => void)(slot);

      expect(dialog.open).not.toHaveBeenCalled();
    });

    it('opens a whole-day slot for a click in the month, which arrives as a one-day selection', () => {
      show();

      (options().select as (info: unknown) => void)({ startStr: '2026-12-24', endStr: '2026-12-25', allDay: true });

      expect(dialog.open.mock.calls[0][1].data.slot).toEqual({ start: '2026-12-24', end: '2026-12-25', allDay: true });
    });

    it('registers no date-click handler besides the selection, because FullCalendar fires both for one click and two dialogs opened', () => {
      show();

      expect(options().dateClick).toBeUndefined();
    });

    it('reads the calendar again when the dialog changed something, and not when it was only closed', () => {
      show();
      dialog.open.mockReturnValueOnce({ afterClosed: () => of('saved') }).mockReturnValueOnce({ afterClosed: () => of(undefined) });

      (options().select as (info: unknown) => void)(slot);
      expect(stub().api.refetchEvents).toHaveBeenCalledTimes(1);

      (options().select as (info: unknown) => void)(slot);
      expect(stub().api.refetchEvents).toHaveBeenCalledTimes(1);
    });
  });

  describe('clicking an entry', () => {
    const click = (entryId = 'e-1') => (options().eventClick as (info: unknown) => void)({ event: { extendedProps: { entryId, calendarKey: 'team' } } });

    it('opens an entry the caller may change for editing and tells the host which one', async () => {
      show();
      const selected: string[] = [];
      fixture.componentInstance.entrySelected.subscribe((id) => selected.push(id));

      click();
      await vi.waitFor(() => expect(dialog.open).toHaveBeenCalled());

      expect(selected).toEqual(['e-1']);
      expect(store.getEntry).toHaveBeenCalledWith('team', 'e-1');
      expect(dialog.open.mock.calls[0][1].data).toMatchObject({ mode: 'edit', entry: ENTRY });
    });

    it('opens an entry the caller may not change for reading', async () => {
      store.getEntry.mockResolvedValue({ ...ENTRY, canEdit: false });
      show();

      click();
      await vi.waitFor(() => expect(dialog.open).toHaveBeenCalled());

      expect(dialog.open.mock.calls[0][1].data.mode).toBe('view');
    });

    it('opens an entry for reading on a read-only calendar even when the caller owns it', async () => {
      show([calendar('team')], () => fixture.componentRef.setInput('readOnly', true));

      click();
      await vi.waitFor(() => expect(dialog.open).toHaveBeenCalled());

      expect(dialog.open.mock.calls[0][1].data.mode).toBe('view');
    });

    it('opens nothing when the entry cannot be read any more', async () => {
      store.getEntry.mockRejectedValue(new HttpErrorResponse({ status: 404 }));
      show();

      click();
      await vi.waitFor(() => expect(store.getEntry).toHaveBeenCalled());
      await Promise.resolve();

      expect(dialog.open).not.toHaveBeenCalled();
    });
  });

  describe('dragging and resizing', () => {
    function change(overrides: Record<string, unknown> = {}) {
      const revert = vi.fn();
      const info = {
        event: { extendedProps: { entryId: 'e-1', calendarKey: 'team' }, startStr: '2026-12-02T10:00:00+01:00', endStr: '2026-12-02T12:00:00+01:00', allDay: false, ...overrides },
        revert,
      };
      return { info, revert };
    }

    it('writes a timed entry back with the new instants', async () => {
      show();
      const { info, revert } = change();

      (options().eventDrop as (info: unknown) => void)(info);
      await vi.waitFor(() => expect(store.reschedule).toHaveBeenCalled());

      expect(store.reschedule).toHaveBeenCalledWith('team', 'e-1', { allDay: false, start: '2026-12-02T09:00:00Z', end: '2026-12-02T11:00:00Z' });
      expect(revert).not.toHaveBeenCalled();
    });

    it('writes an all-day entry back by its dates, and a one-day one with no end as ending the next day', async () => {
      show();

      (options().eventResize as (info: unknown) => void)(change({ allDay: true, startStr: '2026-12-24', endStr: '2026-12-27' }).info);
      (options().eventDrop as (info: unknown) => void)(change({ allDay: true, startStr: '2026-12-24', endStr: '' }).info);
      await vi.waitFor(() => expect(store.reschedule).toHaveBeenCalledTimes(2));

      expect(store.reschedule).toHaveBeenNthCalledWith(1, 'team', 'e-1', { allDay: true, startDate: '2026-12-24', endDate: '2026-12-27' });
      expect(store.reschedule).toHaveBeenNthCalledWith(2, 'team', 'e-1', { allDay: true, startDate: '2026-12-24', endDate: '2026-12-25' });
    });

    it('puts the event back when the server refuses, and leaves the saying to the central error handler', async () => {
      store.reschedule.mockRejectedValue(new HttpErrorResponse({ status: 422 }));
      show();
      const { info, revert } = change();

      (options().eventDrop as (info: unknown) => void)(info);
      await vi.waitFor(() => expect(revert).toHaveBeenCalled());
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="calendar-notice"]')).toBeNull();
    });

    it('puts the event back and explains when a drag would change its kind', async () => {
      store.reschedule.mockRejectedValue(new KindChangeError());
      show();
      const { info, revert } = change();

      (options().eventDrop as (info: unknown) => void)(info);
      await vi.waitFor(() => expect(revert).toHaveBeenCalled());
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="calendar-notice"]').textContent).toContain('cannot be turned from timed into all-day');
    });
  });
});
