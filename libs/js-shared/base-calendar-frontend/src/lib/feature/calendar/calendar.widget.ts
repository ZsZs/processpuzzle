import { Provider } from '@angular/core';
import { provideWidget, WidgetRegistration } from '@processpuzzle/widgets';

export const CALENDAR_WIDGET = 'calendar';

/**
 * Registered here, not in base-widget-frontend: the widget talks to base-calendar's API, and the widget library
 * must not depend on a feature library. The seed catalogue carries a copy of this definition all the same, so a
 * designer can place it; `calendar-widgets.contract.spec.ts` keeps the two equal.
 *
 * The props are presentation only. Who may see or book a calendar is the calendar definition's business, enforced
 * by the server; nothing a designer sets here widens it.
 */
export const CALENDAR_WIDGET_REGISTRATION: WidgetRegistration = {
  type: CALENDAR_WIDGET,
  // Lazy: FullCalendar and its time-zone polyfill are fetched when a page first places the calendar, not with the app.
  loadComponent: () => import('./calendar.component').then((module) => module.CalendarComponent),
  definition: {
    name: 'Calendar',
    translocoId: 'base_calendar.widgets.calendar',
    description: 'One or more calendars as a day, week, month or list view, with booking. Who sees and books what is set on the calendars themselves.',
    category: 'Content',
    icon: 'calendar_month',
    propsSchema: {
      type: 'object',
      properties: {
        calendarKeys: {
          type: 'array',
          title: 'Calendars',
          description: 'Keys of the calendars to show, overlaid in their own colors. A calendar the visitor may not read is left out.',
          items: { type: 'string' },
        },
        defaultView: {
          type: 'string',
          enum: ['day', 'week', 'month', 'list'],
          title: 'First view',
          description: 'The view shown first; the week when left empty.',
        },
        views: {
          type: 'array',
          title: 'Views',
          description: 'The views the visitor can switch between, from day, week, month and list; all four when left empty.',
          items: { type: 'string' },
        },
        firstDayOfWeek: {
          type: 'integer',
          title: 'First day of the week',
          description: '0 for Sunday, 1 for Monday; Monday when left empty.',
        },
        slotMinTime: {
          type: 'string',
          title: 'Day starts at',
          description: 'The earliest hour the day and week views show, as HH:mm; 07:00 when left empty.',
        },
        slotMaxTime: {
          type: 'string',
          title: 'Day ends at',
          description: 'The latest hour the day and week views show, as HH:mm; 20:00 when left empty.',
        },
        readOnly: {
          type: 'boolean',
          title: 'Read only',
          description: 'Show the calendar without letting the visitor book, move or resize anything.',
        },
        includeMine: {
          type: 'boolean',
          title: 'Include my invitations',
          description: 'Also show the entries I organize or am invited to, from every calendar I may read.',
        },
        height: {
          type: 'string',
          title: 'Height',
          description: 'A CSS length such as 640px; the height of the content when left empty.',
        },
      },
      additionalProperties: false,
    },
    outputPorts: [
      {
        name: 'entrySelected',
        type: 'STRING',
        description: 'Id of the entry the visitor clicked.',
      },
      {
        name: 'slotSelected',
        type: 'OBJECT',
        description: 'The empty slot the visitor clicked or dragged: its start, its exclusive end and whether it is a whole day.',
      },
    ],
  },
};

export function provideCalendarWidget(): Provider[] {
  return provideWidget(CALENDAR_WIDGET_REGISTRATION);
}
