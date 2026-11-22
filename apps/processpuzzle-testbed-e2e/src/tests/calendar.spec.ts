import { BrowserContext, expect, test } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { serviceRoots, ServiceRoots } from '../support/race-sample.api';
import { registerAndSignInSecondUser, TWO_USER_CHAT_ENABLED } from '../support/second-user';
import { bookTimedEntry, calendarOf, cancelAll, clickEmptySlot, openSample, respondsTo, showWeek, trackBookings } from '../support/calendar-page';

/**
 * The calendars the testbed seeds (`processpuzzle-testbed-calendar-definitions.yaml`), through the real stack.
 *
 * - `public-events` is readable by anybody, anonymous visitors included, and every member books on it.
 * - `team-calendar` is for signed-in members.
 * - `meeting-room-a` is for members too, but only holders of the `staff` realm role book on it. The testbed realm has
 *   no roles, so a self-registered test user never does; that is what makes it a test of the role rules.
 *
 * Writing always needs a token, and Keycloak does not allow the password grant for this client, so the bookings are made
 * through the page. Each spec records what it booked and the token the page used, and removes the bookings afterwards
 * with them.
 */

test.describe('Calendar, anonymous visitors', () => {
  let roots: ServiceRoots;
  const nextWeek = () => ({ from: new Date().toISOString(), to: new Date(Date.now() + 7 * 24 * 3600 * 1000).toISOString() });

  test.beforeEach(async ({ request, baseURL }) => {
    roots = await serviceRoots(request, baseURL as string);
  });

  test('read a public calendar, with no role lists and nothing they may do', async ({ request }) => {
    const response = await request.get(`${roots.backend}/calendar/calendars/public-events`);

    expect(response.status(), await response.text()).toBe(200);
    const calendar = await response.json();
    expect(calendar).toMatchObject({ key: 'public-events', kind: 'TEAM', permissions: { canBook: false, canManage: false } });
    expect(calendar.effectiveTimeZone).toEqual(expect.any(String));
    // What a visitor learns of a calendar is what it does, never who is let in.
    expect(calendar).not.toHaveProperty('readRoles');
    expect(calendar).not.toHaveProperty('bookRoles');
    expect(calendar).not.toHaveProperty('manageRoles');
  });

  test('cannot tell a calendar that is for members from one that does not exist', async ({ request }) => {
    const hidden = await request.get(`${roots.backend}/calendar/calendars/team-calendar`);
    const room = await request.get(`${roots.backend}/calendar/calendars/meeting-room-a`);
    const missing = await request.get(`${roots.backend}/calendar/calendars/no-such-calendar`);

    for (const response of [hidden, room, missing]) {
      expect(response.status()).toBe(404);
      expect((await response.json()).errorId).toBe('calendar.not-found');
    }
    expect(await hidden.json()).toEqual(await missing.json());
  });

  test('see only the public calendar in a window, and an empty answer for the others', async ({ request }) => {
    const { from, to } = nextWeek();

    const response = await request.get(`${roots.backend}/calendar/occurrences`, { params: { calendars: 'public-events,team-calendar,meeting-room-a', from, to } });

    expect(response.status(), await response.text()).toBe(200);
    const occurrences = (await response.json()) as Array<{ calendarKey: string; busy: boolean; canEdit?: boolean }>;
    expect(occurrences.every((occurrence) => occurrence.calendarKey === 'public-events')).toBe(true);
    expect(occurrences.every((occurrence) => occurrence.canEdit !== true)).toBe(true);
  });

  test('cannot ask for more than a hundred days at once', async ({ request }) => {
    const from = new Date();
    const to = new Date(from.getTime() + 101 * 24 * 3600 * 1000);

    const response = await request.get(`${roots.backend}/calendar/occurrences`, { params: { calendars: 'public-events', from: from.toISOString(), to: to.toISOString() } });

    expect(response.status()).toBe(400);
    expect((await response.json()).errorId).toBe('calendar.invalid-request');
  });

  test('cannot book, change or cancel anything, not even on the public calendar', async ({ request }) => {
    const base = `${roots.backend}/calendar/calendars/public-events/entries`;
    const entry = { kind: 'TIMED', title: 'Anonymous', start: new Date().toISOString(), end: new Date(Date.now() + 3600 * 1000).toISOString() };
    const someEntry = randomUUID();

    expect((await request.post(base, { data: entry })).status()).toBe(401);
    expect((await request.put(`${base}/${someEntry}`, { data: entry })).status()).toBe(401);
    expect((await request.delete(`${base}/${someEntry}`)).status()).toBe(401);
  });
});

test.describe('Calendar @auth', () => {
  test.describe.configure({ timeout: 180_000 });

  let roots: ServiceRoots;
  let bookings: ReturnType<typeof trackBookings>;
  let second: BrowserContext | undefined;
  let title: string;

  test.beforeEach(async ({ page, request, baseURL }) => {
    roots = await serviceRoots(request, baseURL as string);
    bookings = trackBookings(page);
    title = `E2E calendar ${randomUUID().slice(0, 8)}`;
  });

  // Cancelling what a failed spec left booked, with the token the page used; one the spec cancelled itself is a 404.
  test.afterEach(async ({ request }) => {
    await second?.close();
    second = undefined;
    await cancelAll(request, roots, bookings);
  });

  test('a member books an entry on the public calendar, changes it and cancels it', async ({ page }) => {
    await openSample(page, 'public');
    await showWeek(page);
    await bookTimedEntry(page, title);

    // The entry is the member's own: clicking it opens it for editing, with what was typed.
    await calendarOf(page).getByText(title).click();
    await expect(page.getByTestId('entry-dialog-title')).toHaveText('Edit entry');
    await expect(page.getByTestId('entry-title')).toHaveValue(title);
    await page.getByTestId('entry-title').fill(`${title} renamed`);
    await page.getByTestId('entry-save').click();
    await expect(page.getByTestId('entry-dialog-title')).toBeHidden({ timeout: 30_000 });
    await expect(calendarOf(page).getByText(`${title} renamed`)).toBeVisible({ timeout: 30_000 });
    await expect(calendarOf(page).getByText(title, { exact: true })).toHaveCount(0);

    // Cancelling takes a second, explicit step, and the entry is gone afterwards.
    await calendarOf(page).getByText(`${title} renamed`).click();
    await page.getByTestId('entry-cancel').click();
    await page.getByTestId('entry-cancel-confirmed').click();
    await expect(page.getByTestId('entry-dialog-title')).toBeHidden({ timeout: 30_000 });
    await expect(calendarOf(page).getByText(`${title} renamed`)).toHaveCount(0, { timeout: 30_000 });
  });

  test('a member without the staff role reads the meeting room but cannot book on it', async ({ page, request }) => {
    const team = page.waitForResponse(respondsTo(/\/calendar\/calendars\/team-calendar$/));
    const room = page.waitForResponse(respondsTo(/\/calendar\/calendars\/meeting-room-a$/));
    await openSample(page, 'overlay');

    // The roles are read from the real token: any member books on the team calendar, only `staff` on the room.
    expect((await (await team).json()).permissions).toEqual({ canBook: true, canManage: false });
    const meetingRoom = await (await room).json();
    expect(meetingRoom.permissions).toEqual({ canBook: false, canManage: false });
    expect(meetingRoom).toMatchObject({ kind: 'RESOURCE', allowedKinds: ['TIMED'], slotMinutes: 30 });
    await expect(page.getByTestId('calendar-error')).toBeHidden();

    // A new entry can only go on the team calendar, so the dialog does not ask which calendar.
    // The view switcher is a tab list, one tab per view, named after the view.
  await calendarOf(page).getByRole('tab', { name: 'Week view' }).click();
    await clickEmptySlot(page);
    await expect(page.getByTestId('entry-dialog-title')).toHaveText('New entry');
    await expect(page.getByTestId('entry-calendar')).toHaveCount(0);
    await page.getByTestId('entry-close').click();

    // And the server holds the same line when asked directly: signed in, may read, may not book.
    expect(bookings.token, 'the page sent no token to the calendar').toBeTruthy();
    const refused = await request.post(`${roots.backend}/calendar/calendars/meeting-room-a/entries`, {
      headers: { authorization: bookings.token as string },
      data: { kind: 'TIMED', title: 'No staff role', start: '2030-01-07T09:00:00Z', end: '2030-01-07T10:00:00Z' },
    });
    expect(refused.status(), await refused.text()).toBe(403);
    expect((await refused.json()).errorId).toBe('calendar.access-denied');
  });

  test('a booking that breaks the rules of its calendar is refused with the reason', async ({ page, request }) => {
    await openSample(page, 'public');
    expect(bookings.token, 'the page sent no token to the calendar').toBeTruthy();

    const refused = await request.post(`${roots.backend}/calendar/calendars/public-events/entries`, {
      headers: { authorization: bookings.token as string },
      data: { kind: 'RECURRING', title: 'Weekly', start: '2030-01-07T09:00:00Z', end: '2030-01-07T10:00:00Z' },
    });

    expect(refused.status()).toBe(422);
    expect((await refused.json()).errorText).toContain('does not accept RECURRING entries');
  });

  // Opt-in, like the two-person chat test: it registers a second account and signs it in through the login form.
  const twoUser = TWO_USER_CHAT_ENABLED ? test : test.skip;

  twoUser('a private entry is only busy time to another member', async ({ page, browser, request, baseURL }) => {
    test.setTimeout(300_000);
    await openSample(page, 'team');
    await bookTimedEntry(page, title, { private: true });
    const entry = bookings.created[0];
    expect(entry, 'the booking was not recorded').toBeDefined();

    // Somebody else, signed in under their own account, finds the time taken and nothing of what it is.
    const registered = await registerAndSignInSecondUser(browser, request, baseURL as string);
    second = registered.context;
    const other = await second.newPage();
    // Every window the other member's page asks for, as the server answered it. The entry is found in any of them.
    const answers: Array<Array<{ entryId: string; busy: boolean; title?: string; canEdit?: boolean }>> = [];
    other.on('response', async (response) => {
      if (response.request().method() === 'GET' && /\/calendar\/occurrences/.test(response.url()) && response.ok()) answers.push(await response.json());
    });
    await openSample(other, 'team');

    await expect
      .poll(() => answers.flat().some((occurrence) => occurrence.entryId === entry.id), { timeout: 30_000, message: 'the other member never got the entry in a window' })
      .toBe(true);
    const seen = answers.flat().find((occurrence) => occurrence.entryId === entry.id);
    expect(seen).toMatchObject({ busy: true });
    expect(seen?.title ?? null).toBeNull();
    expect(seen?.canEdit ?? false).toBe(false);
    await expect(calendarOf(other).getByText('Busy').first()).toBeVisible({ timeout: 30_000 });
    await expect(calendarOf(other).getByText(title)).toHaveCount(0);

    // The organizer still sees it in full.
    await expect(calendarOf(page).getByText(title)).toBeVisible();
  });
});
