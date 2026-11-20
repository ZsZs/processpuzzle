import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked, viewChild, ViewContainerRef } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatDialog } from '@angular/material/dialog';
import { CalendarOptions, DateClickInfo, DateSelectInfo, EventClickInfo, EventDropInfo, EventResizeDoneInfo, EventSourceFuncInfo, FullCalendarComponent, FullCalendarModule } from '@fullcalendar/angular';
import dayGridPlugin from '@fullcalendar/angular/daygrid';
import interactionPlugin from '@fullcalendar/angular/interaction';
import listPlugin from '@fullcalendar/angular/list';
import classicTheme from '@fullcalendar/angular/themes/classic';
import timeGridPlugin from '@fullcalendar/angular/timegrid';
import deLocale from 'fullcalendar/locales/de';
import esLocale from 'fullcalendar/locales/es';
import frLocale from 'fullcalendar/locales/fr';
import huLocale from 'fullcalendar/locales/hu';
import { provideTranslocoScope, TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { BASE_CALENDAR_TRANSLOCO_ALIAS, BASE_CALENDAR_TRANSLOCO_SCOPE } from '../../base-calendar.i18n';
import { CalendarStore, KindChangeError, Reschedule } from '../../domain/calendar.store';
import { CalendarSlot } from '../../domain/calendar.types';
import { toCalendarEvent } from '../../domain/occurrence-events';
import { datePart, nextDay, plusMinutes, toInstant } from '../../domain/zoned-time';
import { EntryDialogComponent, EntryDialogData, EntryDialogResult } from '../entry-dialog/entry-dialog.component';

/** The views a designer can offer, and the FullCalendar view each stands for. */
const VIEW_NAMES = { day: 'timeGridDay', week: 'timeGridWeek', month: 'dayGridMonth', list: 'listWeek' } as const;
export type CalendarViewName = keyof typeof VIEW_NAMES;
const ALL_VIEWS = Object.keys(VIEW_NAMES) as CalendarViewName[];

/** The languages the platform ships that FullCalendar has texts for besides English. */
const LOCALES = [deLocale, esLocale, frLocale, huLocale];

/** The user's language as FullCalendar knows it; English when it has no texts for it. */
function localeOf(language: string): string {
  const code = language.split('-')[0];
  return LOCALES.some((locale) => locale.code === code) ? code : 'en';
}

function isCalendarView(name: string): name is CalendarViewName {
  return name in VIEW_NAMES;
}

/**
 * The `calendar` widget: one or more calendars as a day, week, month or list view, with booking.
 *
 * **What it shows.** The calendars named by `calendarKeys`, overlaid, each in its own color, in the organization's
 * time zone. A calendar the caller may not read is left out; if none can be shown the widget says so. Only the
 * calendars' rules and the caller's rights decide what is offered: a caller who may not book gets a read-only calendar
 * whatever `readOnly` says, one who sees an entry as busy sees only the time, and only an entry the caller may change
 * can be dragged or resized.
 *
 * **Booking.** Clicking or dragging an empty slot opens the entry dialog; clicking an entry opens it for editing or,
 * when it is not the caller's to change, for reading. A drag or a resize writes the entry back at once, and puts it
 * back where it was if the server refuses.
 *
 * FullCalendar is imported here and nowhere else in the library.
 */
@Component({
  selector: 'pp-calendar',
  standalone: true,
  imports: [FullCalendarModule, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [CalendarStore, provideTranslocoScope({ scope: BASE_CALENDAR_TRANSLOCO_SCOPE, alias: BASE_CALENDAR_TRANSLOCO_ALIAS })],
  template: `
    <section class="pp-calendar" [attr.aria-label]="'base_calendar.title' | transloco">
      @if (notice(); as message) {
        <p class="pp-calendar__notice" role="status" data-testid="calendar-notice">{{ message | transloco }}</p>
      }
      @switch (store.status()) {
        @case ('idle') {
          <p class="pp-calendar__hint" data-testid="calendar-none">{{ 'base_calendar.no_calendar' | transloco }}</p>
        }
        @case ('loading') {
          <p class="pp-calendar__hint" role="status" data-testid="calendar-loading">{{ 'base_calendar.loading' | transloco }}</p>
        }
        @case ('error') {
          <p class="pp-calendar__hint" role="alert" data-testid="calendar-error">{{ failureKey() | transloco }}</p>
        }
        @default {
          <full-calendar data-testid="calendar" [options]="options()" />
        }
      }
    </section>
  `,
  styles: `
    :host {
      display: block;
    }
    .pp-calendar__hint,
    .pp-calendar__notice {
      margin: 0.5rem 0;
    }
  `,
})
export class CalendarComponent {
  protected readonly store = inject(CalendarStore);
  private readonly dialog = inject(MatDialog);
  private readonly viewContainer = inject(ViewContainerRef);
  private readonly transloco = inject(TranslocoService);

  /** Keys of the calendars to show, overlaid. */
  readonly calendarKeys = input<string[]>([]);
  /** The view shown first. */
  readonly defaultView = input<string>('week');
  /** The views the user can switch between, in this order; the ones that exist, all four when left empty. */
  readonly views = input<string[]>(ALL_VIEWS);
  /** The first day of the week, 0 for Sunday. */
  readonly firstDayOfWeek = input<number>(1);
  /** The earliest hour the day and week views show, `HH:mm`. */
  readonly slotMinTime = input<string>('07:00');
  /** The latest hour the day and week views show, `HH:mm`. */
  readonly slotMaxTime = input<string>('20:00');
  /** Show the calendar without letting the user book, move or resize anything. */
  readonly readOnly = input<boolean>(false);
  /** The height: `auto` grows with the content, a CSS length such as `640px` fixes it. */
  readonly height = input<string>('auto');

  /** The id of the entry the user clicked. */
  readonly entrySelected = output<string>();
  /** The slot the user clicked or dragged, before the dialog opens. */
  readonly slotSelected = output<CalendarSlot>();

  private readonly calendar = viewChild(FullCalendarComponent);
  private readonly language = toSignal(this.transloco.langChanges$, { initialValue: this.transloco.getActiveLang() });

  protected readonly notice = signal<string | null>(null);

  protected readonly failureKey = computed(() => {
    switch (this.store.failure()) {
      case 'not-found':
      case 'forbidden':
        return 'base_calendar.errors.not_found';
      default:
        return 'base_calendar.errors.unavailable';
    }
  });

  /** The views offered, in the designer's order; all of them when none of the names is one. */
  private readonly offeredViews = computed(() => {
    const named = this.views().filter(isCalendarView);
    return named.length > 0 ? [...new Set(named)] : ALL_VIEWS;
  });

  private readonly editable = computed(() => !this.readOnly() && this.store.canBook());

  protected readonly options = computed<CalendarOptions>(() => {
    const offered = this.offeredViews();
    const requested = this.defaultView();
    const initial = isCalendarView(requested) && offered.includes(requested) ? requested : offered[0];
    const editable = this.editable();
    return {
      plugins: [classicTheme, dayGridPlugin, timeGridPlugin, listPlugin, interactionPlugin],
      locales: LOCALES,
      locale: localeOf(this.language()),
      initialView: VIEW_NAMES[initial],
      timeZone: this.store.timeZone(),
      firstDay: this.firstDayOfWeek(),
      slotMinTime: this.slotMinTime(),
      slotMaxTime: this.slotMaxTime(),
      height: this.height(),
      nowIndicator: true,
      navLinks: true,
      dayMaxEvents: true,
      headerToolbar: { start: 'prev,next today', center: 'title', end: offered.map((view) => VIEW_NAMES[view]).join(',') },
      selectable: editable,
      editable,
      events: (info: EventSourceFuncInfo) => this.fetchEvents(info),
      select: (info: DateSelectInfo) => this.onSelect(info),
      dateClick: (info: DateClickInfo) => this.onDateClick(info),
      eventClick: (info: EventClickInfo) => void this.onEventClick(info),
      eventDrop: (info: EventDropInfo) => void this.onEventChange(info),
      eventResize: (info: EventResizeDoneInfo) => void this.onEventChange(info),
    };
  });

  constructor() {
    effect(() => {
      const keys = this.calendarKeys();
      untracked(() => void this.store.loadCalendars(keys));
    });
  }

  /** The occurrences of a window, as events. A failure is the central error handler's to report; here it only stops the load. */
  private async fetchEvents(info: EventSourceFuncInfo) {
    const zone = this.store.timeZone();
    const occurrences = await this.store.loadOccurrences(toInstant(info.startStr, zone), toInstant(info.endStr, zone));
    const keys = this.store.calendars().map((calendar) => calendar.key);
    const busy = this.transloco.translate('base_calendar.busy');
    return occurrences.map((occurrence) => toCalendarEvent(occurrence, keys.indexOf(occurrence.calendarKey), busy));
  }

  private onSelect(info: DateSelectInfo): void {
    this.calendar()?.getApi().unselect();
    this.openCreate({ start: info.startStr, end: info.endStr, allDay: info.allDay });
  }

  /** A click on a slot rather than a drag: a whole day on the all-day row and in the month, else a start to default the end from. */
  private onDateClick(info: DateClickInfo): void {
    if (!this.editable()) return;
    this.openCreate(info.allDay ? { start: info.dateStr, end: nextDay(datePart(info.dateStr)), allDay: true } : { start: info.dateStr, end: info.dateStr, allDay: false });
  }

  private openCreate(slot: CalendarSlot): void {
    if (!this.editable()) return;
    this.slotSelected.emit(slot);
    const calendars = this.store.calendars().filter((calendar) => calendar.permissions.canBook);
    this.open({ mode: 'create', calendars, slot });
  }

  private async onEventClick(info: EventClickInfo): Promise<void> {
    const { entryId, calendarKey } = info.event.extendedProps as { entryId: string; calendarKey: string };
    this.entrySelected.emit(entryId);
    const entry = await this.store.getEntry(calendarKey, entryId).catch(() => null);
    if (!entry) return;
    const calendars = this.store.calendars();
    this.open({ mode: entry.canEdit && this.editable() ? 'edit' : 'view', calendars, entry });
  }

  /** A drag or a resize writes the entry back; if that is refused the event goes back to where it was. */
  private async onEventChange(info: EventDropInfo | EventResizeDoneInfo): Promise<void> {
    const { entryId, calendarKey } = info.event.extendedProps as { entryId: string; calendarKey: string };
    try {
      await this.store.reschedule(calendarKey, entryId, this.timesOf(info.event.startStr, info.event.endStr, info.event.allDay));
      this.notice.set(null);
    } catch (error) {
      info.revert();
      // An HTTP refusal is already reported by the central error handler; only the local one needs saying.
      this.notice.set(error instanceof KindChangeError ? 'base_calendar.errors.kind_change' : null);
    }
  }

  private timesOf(startStr: string, endStr: string, allDay: boolean): Reschedule {
    const zone = this.store.timeZone();
    if (allDay) {
      const startDate = datePart(startStr);
      return { allDay, startDate, endDate: endStr ? datePart(endStr) : nextDay(startDate) };
    }
    const start = toInstant(startStr, zone);
    return { allDay, start, end: endStr ? toInstant(endStr, zone) : plusMinutes(start, 60) };
  }

  private open(data: EntryDialogData): void {
    if (data.calendars.length === 0) return;
    this.dialog
      .open<EntryDialogComponent, EntryDialogData, EntryDialogResult>(EntryDialogComponent, { data, viewContainerRef: this.viewContainer, autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.calendar()?.getApi().refetchEvents();
        }
      });
  }
}
