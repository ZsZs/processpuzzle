import { APIRequestContext, expect, Locator, Page, Response } from '@playwright/test';
import { ServiceRoots } from './race-sample.api';

/** Calendar calls only: what the page sends is the one place a real token can be seen. */
export function trackBookings(page: Page) {
  const state = { token: undefined as string | undefined, created: [] as Array<{ calendarKey: string; id: string }> };
  page.on('request', async (request) => {
    if (!request.url().includes('/calendar/')) return;
    const authorization = (await request.allHeaders())['authorization'];
    if (authorization) state.token = authorization;
  });
  page.on('response', async (response) => {
    if (response.request().method() !== 'POST' || response.status() !== 201 || !/\/calendar\/calendars\/[^/]+\/entries$/.test(response.url())) return;
    const entry = (await response.json()) as { id: string; calendarKey: string };
    state.created.push({ calendarKey: entry.calendarKey, id: entry.id });
  });
  return state;
}

export const calendarOf = (page: Page): Locator => page.getByTestId('calendar');

/** The Base Calendar samples page with one of its sets of calendars in the widget. */
export async function openSample(page: Page, sample: 'public' | 'team' | 'overlay'): Promise<void> {
  await page.goto('/base-calendar/samples');
  if (sample !== 'public') await page.getByTestId('calendar-sample-select').selectOption(sample);
  await expect(calendarOf(page)).toBeVisible({ timeout: 60_000 });
}

export async function showWeek(page: Page): Promise<void> {
  // The view switcher is a tab list, one tab per view, named after the view.
  await calendarOf(page).getByRole('tab', { name: 'Week view' }).click();
  await expect(calendarOf(page).getByText('9am', { exact: true })).toBeVisible();
}

/** FullCalendar 7 hashes its class names, so an empty slot is found by position: the Wednesday column, at the 9am row. */
export async function clickEmptySlot(page: Page): Promise<void> {
  const column = await calendarOf(page)
    .getByText(/^Wed \d+$/)
    .first()
    .boundingBox();
  const hour = await calendarOf(page).getByText('9am', { exact: true }).first().boundingBox();
  if (!column || !hour) throw new Error('the week grid is not on the page');
  await page.mouse.click(column.x + column.width / 2, hour.y + hour.height / 2);
}

/** Books a timed entry in the empty Wednesday 9am slot of the week on show, through the dialog. */
export async function bookTimedEntry(page: Page, title: string, options: { private?: boolean } = {}): Promise<void> {
  await clickEmptySlot(page);
  await expect(page.getByTestId('entry-dialog-title')).toHaveText('New entry');
  await page.getByTestId('entry-title').fill(title);
  if (options.private) {
    // An empty mat-select swallows a click on its label; the keyboard opens it whatever owns the pixel.
    // The option list can render after the picker does, so the keypress is retried until the option shows.
    const visibility = page.getByTestId('entry-visibility');
    const privateOption = page.getByRole('option', { name: /^Private/ });
    await expect(async () => {
      await visibility.focus();
      await visibility.press('Enter');
      await expect(privateOption).toBeVisible({ timeout: 2_000 });
    }).toPass({ timeout: 30_000 });
    await privateOption.click();
  }
  await page.getByTestId('entry-save').click();
  await expect(page.getByTestId('entry-dialog-title')).toBeHidden({ timeout: 30_000 });
  await expect(calendarOf(page).getByText(title)).toBeVisible({ timeout: 30_000 });
}

export const respondsTo = (path: RegExp, method = 'GET') => (response: Response) => response.request().method() === method && path.test(response.url()) && response.ok();

export async function cancelAll(request: APIRequestContext, roots: ServiceRoots, bookings: { token?: string; created: Array<{ calendarKey: string; id: string }> }): Promise<void> {
  if (!bookings.token) return;
  for (const { calendarKey, id } of bookings.created) {
    const response = await request.delete(`${roots.backend}/calendar/calendars/${calendarKey}/entries/${id}`, { headers: { authorization: bookings.token } });
    expect([204, 404], `cancelling ${id}: ${response.status()}`).toContain(response.status());
  }
}
