/** One set of calendars the samples page can show. */
export interface CalendarSample {
  id: string;
  /** Key of its name in the drop-down, under the page's own `base-calendar` translation prefix. */
  labelKey: string;
  /** Keys of the seeded calendar definitions the widget shows, overlaid. */
  calendarKeys: string[];
  /** The view the widget opens on. */
  defaultView: 'day' | 'week' | 'month' | 'list';
}

/**
 * What the samples page offers, over the calendars `processpuzzle-testbed-calendar-definitions.yaml` seeds: a public
 * one that any visitor can read, one for signed-in members, and a room bookable by staff. The first is the default,
 * because it is the only one that shows something to a visitor who has not signed in.
 */
export const CALENDAR_SAMPLES: readonly CalendarSample[] = [
  { id: 'public', labelKey: 'sample_public', calendarKeys: ['public-events'], defaultView: 'month' },
  { id: 'team', labelKey: 'sample_team', calendarKeys: ['team-calendar'], defaultView: 'week' },
  { id: 'overlay', labelKey: 'sample_overlay', calendarKeys: ['team-calendar', 'meeting-room-a'], defaultView: 'week' },
];
