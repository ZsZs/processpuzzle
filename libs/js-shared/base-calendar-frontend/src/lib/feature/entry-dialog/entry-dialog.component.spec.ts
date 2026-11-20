import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CalendarStore } from '../../domain/calendar.store';
import { CalendarEntry, CalendarInfo } from '../../domain/calendar.types';
import { calendarTranslations } from '../../test-translations';
import { EntryDialogComponent, EntryDialogData } from './entry-dialog.component';

const TEAM: CalendarInfo = {
  key: 'team',
  name: 'Team calendar',
  kind: 'TEAM',
  effectiveTimeZone: 'Europe/Budapest',
  allowedKinds: ['TIMED', 'ALL_DAY'],
  permissions: { canBook: true, canManage: false },
};

const ROOM: CalendarInfo = { ...TEAM, key: 'room', name: 'Meeting room', kind: 'RESOURCE', allowedKinds: ['TIMED'] };

const SLOT = { start: '2026-12-01T10:00:00+01:00', end: '2026-12-01T11:00:00+01:00', allDay: false };

const ENTRY: CalendarEntry = {
  id: 'e-1',
  calendarKey: 'team',
  kind: 'TIMED',
  busy: false,
  title: 'Planning',
  description: 'Quarterly planning',
  location: 'Room 1',
  category: 'meeting',
  visibility: 'PRIVATE',
  start: '2026-12-01T09:00:00Z',
  end: '2026-12-01T10:00:00Z',
  organizerDisplayName: 'Ann Organizer',
  canEdit: true,
  version: 3,
};

describe('EntryDialogComponent', () => {
  let store: { book: ReturnType<typeof vi.fn>; update: ReturnType<typeof vi.fn>; cancel: ReturnType<typeof vi.fn> };
  let dialogRef: { close: ReturnType<typeof vi.fn> };
  let fixture: ComponentFixture<EntryDialogComponent>;

  async function open(data: EntryDialogData) {
    TestBed.configureTestingModule({
      imports: [EntryDialogComponent],
      providers: [
        provideTranslocoTesting({ translations: calendarTranslations() }),
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: CalendarStore, useValue: store },
      ],
    });
    fixture = TestBed.createComponent(EntryDialogComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const q = (testId: string) => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) as HTMLElement | null;
  const text = (testId: string) => q(testId)?.textContent?.trim();

  async function type(testId: string, value: string) {
    const input = q(testId) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function submit() {
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    store = { book: vi.fn().mockResolvedValue(ENTRY), update: vi.fn().mockResolvedValue(ENTRY), cancel: vi.fn().mockResolvedValue(undefined) };
    dialogRef = { close: vi.fn() };
  });

  describe('a new entry', () => {
    it('starts from the clicked slot in the organization wall clock, with nothing typed yet', async () => {
      await open({ mode: 'create', calendars: [TEAM], slot: SLOT });

      expect(text('entry-dialog-title')).toBe('New entry');
      expect((q('entry-start') as HTMLInputElement).value).toBe('2026-12-01T10:00');
      expect((q('entry-end') as HTMLInputElement).value).toBe('2026-12-01T11:00');
      expect((q('entry-title') as HTMLInputElement).value).toBe('');
    });

    it('cannot be saved before it has a title', async () => {
      await open({ mode: 'create', calendars: [TEAM], slot: SLOT });
      expect((q('entry-save') as HTMLButtonElement).disabled).toBe(true);

      await type('entry-title', 'Planning');

      expect((q('entry-save') as HTMLButtonElement).disabled).toBe(false);
    });

    it('is booked as instants with the organization zone, and the dialog closes telling the calendar to reload', async () => {
      await open({ mode: 'create', calendars: [TEAM], slot: SLOT });
      await type('entry-title', '  Planning ');
      await type('entry-location', 'Room 1');

      await submit();

      expect(store.book).toHaveBeenCalledWith('team', {
        kind: 'TIMED',
        title: 'Planning',
        description: null,
        location: 'Room 1',
        category: null,
        visibility: 'PUBLIC',
        version: null,
        start: '2026-12-01T09:00:00Z',
        end: '2026-12-01T10:00:00Z',
        timeZone: 'Europe/Budapest',
      });
      expect(dialogRef.close).toHaveBeenCalledWith('saved');
    });

    it('books nothing while the form is incomplete', async () => {
      await open({ mode: 'create', calendars: [TEAM], slot: SLOT });

      await submit();

      expect(store.book).not.toHaveBeenCalled();
      expect(dialogRef.close).not.toHaveBeenCalled();
    });

    it('stays open with the servers own reason when the booking is refused, keeping what was typed', async () => {
      store.book.mockRejectedValue(new HttpErrorResponse({ status: 422, error: { errorId: 'calendar.booking.invalid', errorText: 'an entry must last at least 30 minutes' } }));
      await open({ mode: 'create', calendars: [TEAM], slot: SLOT });
      await type('entry-title', 'Planning');

      await submit();

      expect(text('entry-error')).toBe('an entry must last at least 30 minutes');
      expect(dialogRef.close).not.toHaveBeenCalled();
      expect((q('entry-title') as HTMLInputElement).value).toBe('Planning');
      expect((q('entry-save') as HTMLButtonElement).disabled).toBe(false);
    });

    it('says something plain when the refusal comes without a reason', async () => {
      store.book.mockRejectedValue(new HttpErrorResponse({ status: 403 }));
      await open({ mode: 'create', calendars: [TEAM], slot: SLOT });
      await type('entry-title', 'Planning');

      await submit();

      expect(text('entry-error')).toBe('You are not allowed to do that.');
    });

    it('offers a whole-day slot as an all-day entry with the inclusive last day', async () => {
      await open({ mode: 'create', calendars: [TEAM], slot: { start: '2026-12-24', end: '2026-12-27', allDay: true } });

      expect((q('entry-start-day') as HTMLInputElement).value).toBe('2026-12-24');
      expect((q('entry-last-day') as HTMLInputElement).value).toBe('2026-12-26');
      expect(q('entry-start')).toBeNull();

      await type('entry-title', 'Holiday');
      await submit();

      expect(store.book).toHaveBeenCalledWith('team', expect.objectContaining({ kind: 'ALL_DAY', startDate: '2026-12-24', endDate: '2026-12-27' }));
    });

    it('asks which calendar only when there is a choice, and which kind only when the calendar takes several', async () => {
      await open({ mode: 'create', calendars: [ROOM], slot: SLOT });
      expect(q('entry-calendar')).toBeNull();
      expect(q('entry-kind')).toBeNull();
      TestBed.resetTestingModule();

      await open({ mode: 'create', calendars: [TEAM, ROOM], slot: SLOT });
      expect(q('entry-calendar')).not.toBeNull();
      expect(q('entry-kind')).not.toBeNull();
    });
  });

  describe('an entry the caller may change', () => {
    it('shows its values in the organization wall clock', async () => {
      await open({ mode: 'edit', calendars: [TEAM], entry: ENTRY });

      expect(text('entry-dialog-title')).toBe('Edit entry');
      expect((q('entry-title') as HTMLInputElement).value).toBe('Planning');
      expect((q('entry-start') as HTMLInputElement).value).toBe('2026-12-01T10:00');
      expect((q('entry-location') as HTMLInputElement).value).toBe('Room 1');
      expect((q('entry-description') as HTMLTextAreaElement).value).toBe('Quarterly planning');
      expect(q('entry-calendar')).toBeNull();
    });

    it('is replaced with the version it was read at, so a concurrent edit is refused rather than overwritten', async () => {
      await open({ mode: 'edit', calendars: [TEAM], entry: ENTRY });
      await type('entry-title', 'Renamed');

      await submit();

      expect(store.update).toHaveBeenCalledWith('team', 'e-1', expect.objectContaining({ title: 'Renamed', version: 3, visibility: 'PRIVATE' }));
      expect(store.book).not.toHaveBeenCalled();
      expect(dialogRef.close).toHaveBeenCalledWith('saved');
    });

    it('tells the user when somebody else changed the entry first', async () => {
      store.update.mockRejectedValue(new HttpErrorResponse({ status: 409 }));
      await open({ mode: 'edit', calendars: [TEAM], entry: ENTRY });

      await submit();

      expect(text('entry-error')).toContain('Somebody changed this entry');
    });

    it('is cancelled only after a second, explicit step', async () => {
      await open({ mode: 'edit', calendars: [TEAM], entry: ENTRY });

      (q('entry-cancel') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(store.cancel).not.toHaveBeenCalled();
      expect(q('entry-save')).toBeNull();

      (q('entry-cancel-confirmed') as HTMLButtonElement).click();
      await fixture.whenStable();

      expect(store.cancel).toHaveBeenCalledWith('team', 'e-1');
      expect(dialogRef.close).toHaveBeenCalledWith('cancelled');
    });

    it('can change its mind about cancelling', async () => {
      await open({ mode: 'edit', calendars: [TEAM], entry: ENTRY });
      (q('entry-cancel') as HTMLButtonElement).click();
      fixture.detectChanges();

      (q('entry-keep') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(store.cancel).not.toHaveBeenCalled();
      expect(q('entry-save')).not.toBeNull();
    });

    it('stays open and says why when the cancellation is refused', async () => {
      store.cancel.mockRejectedValue(new HttpErrorResponse({ status: 403 }));
      await open({ mode: 'edit', calendars: [TEAM], entry: ENTRY });
      (q('entry-cancel') as HTMLButtonElement).click();
      fixture.detectChanges();

      (q('entry-cancel-confirmed') as HTMLButtonElement).click();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(dialogRef.close).not.toHaveBeenCalled();
      expect(text('entry-error')).toBe('You are not allowed to do that.');
      expect(q('entry-cancel')).not.toBeNull();
    });
  });

  describe('an entry the caller may only read', () => {
    it('is shown, not edited, with when and where it is and who organizes it', async () => {
      await open({ mode: 'view', calendars: [TEAM], entry: { ...ENTRY, canEdit: false } });

      expect(text('entry-dialog-title')).toBe('Entry');
      expect(text('entry-view-title')).toBe('Planning');
      expect(text('entry-view-location')).toBe('Room 1');
      expect(text('entry-view-when')).toMatch(/Dec 1, 2026/);
      expect(fixture.nativeElement.textContent).toContain('Ann Organizer');
      expect(fixture.nativeElement.querySelector('form')).toBeNull();
      expect(q('entry-save')).toBeNull();
      expect(q('entry-cancel')).toBeNull();
    });

    it('shows only that the time is taken when the entry is busy', async () => {
      await open({ mode: 'view', calendars: [TEAM], entry: { id: 'e-2', calendarKey: 'team', kind: 'TIMED', busy: true, start: '2026-12-01T09:00:00Z', end: '2026-12-01T10:00:00Z' } });

      expect(text('entry-busy')).toBe('Busy');
      expect(q('entry-view-title')).toBeNull();
      expect(fixture.nativeElement.textContent).not.toContain('Organizer');
    });

    it('writes an all-day entry as a range of days, the last day inclusive', async () => {
      await open({ mode: 'view', calendars: [TEAM], entry: { id: 'e-3', calendarKey: 'team', kind: 'ALL_DAY', busy: false, title: 'Holiday', startDate: '2026-12-24', endDate: '2026-12-27' } });

      expect(text('entry-view-when')).toMatch(/Dec 24, 2026\s+–\s+Dec 26, 2026/);
    });
  });
});
