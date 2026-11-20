import { BrowserContext, expect, test } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { bookTimedEntry, calendarOf, cancelAll, openSample, respondsTo, showWeek, trackBookings } from '../support/calendar-page';
import { serviceRoots, ServiceRoots } from '../support/race-sample.api';
import { registerAndSignInSecondUser, TWO_USER_CHAT_ENABLED } from '../support/second-user';

/**
 * Invitations on a team calendar, through the real stack and two real accounts.
 *
 * The organizer books a private entry and invites the second person by name, found through the directory. The invitee
 * sees the entry in full although it is private, and accepts it. The organizer then moves the entry, which asks the
 * invitee again. Each step is checked against the server, with the token the invitee's own page used.
 *
 * It registers a second account, so it is opt-in like the two-person chat test (`PROCESSPUZZLE_E2E_TWO_USERS=1`).
 */
const twoUser = TWO_USER_CHAT_ENABLED ? test : test.skip;

const DAY = 24 * 3600 * 1000;

let roots: ServiceRoots;
let organizer: ReturnType<typeof trackBookings> | undefined;
let second: BrowserContext | undefined;

test.describe('Calendar invitations @auth', () => {
  test.describe.configure({ timeout: 300_000 });

  test.beforeEach(async ({ page, request, baseURL }) => {
    roots = await serviceRoots(request, baseURL as string);
    organizer = trackBookings(page);
  });

  test.afterEach(async ({ request }) => {
    await second?.close();
    second = undefined;
    if (organizer) await cancelAll(request, roots, organizer);
    organizer = undefined;
  });

  twoUser('an organizer invites a person by name, who sees and accepts the entry, and a move asks again', async ({ page, browser, request, baseURL }) => {
    const title = `E2E invitation ${randomUUID().slice(0, 8)}`;
    await openSample(page, 'team');
    await showWeek(page);
    await bookTimedEntry(page, title, { private: true });
    const entryId = organizer?.created.at(-1)?.id;
    expect(entryId, 'the booking was not recorded').toBeDefined();

    // The second person: a new account, signed in through the login form in a browser of its own.
    const registered = await registerAndSignInSecondUser(browser, request, baseURL as string);
    second = registered.context;
    const invitee = await second.newPage();
    const inviteeCalls = trackBookings(invitee);

    // The organizer invites them by name, found through the directory.
    await calendarOf(page).getByText(title).first().click();
    await expect(page.getByTestId('entry-dialog-title')).toHaveText('Edit entry');
    // The directory matches single words; the run id in the name is one, and is unique to this account.
    const runId = registered.user.displayName.split(' ').at(-1) as string;
    await page.getByTestId('invite-search').fill(runId);
    const match = page.getByTestId('invite-match').filter({ hasText: registered.user.displayName }).first();
    await expect(match).toBeVisible({ timeout: 60_000 });
    await match.click();
    await expect(page.getByTestId('invite-draft').filter({ hasText: registered.user.displayName })).toBeVisible();
    await page.getByTestId('entry-save').click();
    await expect(page.getByTestId('entry-dialog-title')).toBeHidden({ timeout: 30_000 });

    // The invitee opens the entry, which is private, and sees all of it, because they were invited.
    await openSample(invitee, 'team');
    await showWeek(invitee);
    await calendarOf(invitee).getByText(title).first().click();
    await expect(invitee.getByTestId('entry-view-title')).toHaveText(title, { timeout: 30_000 });
    await expect(invitee.getByTestId('entry-busy')).toHaveCount(0);

    // They accept, and the server records it against their name.
    const answered = invitee.waitForResponse(respondsTo(/\/attendance$/, 'PUT'));
    await invitee.getByTestId('rsvp-accept').click();
    const answer = await (await answered).json();
    expect(answer.attendees.find((a: { displayName: string }) => a.displayName === registered.user.displayName)).toMatchObject({ response: 'ACCEPTED' });
    await invitee.getByTestId('entry-close').click();

    // What the invitee's account sees of its invitations, asked directly with their token.
    expect(inviteeCalls.token, 'the invitee page sent no token').toBeTruthy();
    const mine = await request.get(`${roots.backend}/calendar/my-occurrences`, {
      params: { from: new Date(Date.now() - 30 * DAY).toISOString(), to: new Date(Date.now() + 30 * DAY).toISOString() },
      headers: { authorization: inviteeCalls.token as string },
    });
    expect(mine.status(), await mine.text()).toBe(200);
    expect(await mine.json()).toContainEqual(expect.objectContaining({ entryId, busy: false, invited: true }));

    // The organizer moves the entry by two hours. The invitee is asked again.
    await calendarOf(page).getByText(title).first().click();
    await expect(page.getByTestId('entry-dialog-title')).toHaveText('Edit entry');
    const startAt = await page.getByTestId('entry-start').inputValue();
    const endAt = await page.getByTestId('entry-end').inputValue();
    await page.getByTestId('entry-start').fill(startAt.replace(/T\d{2}:\d{2}$/, 'T11:00'));
    await page.getByTestId('entry-end').fill(endAt.replace(/T\d{2}:\d{2}$/, 'T12:00'));
    const moved = page.waitForResponse(respondsTo(/\/entries\/[^/]+$/, 'PUT'));
    await page.getByTestId('entry-save').click();
    await moved;
    await expect(page.getByTestId('entry-dialog-title')).toBeHidden({ timeout: 30_000 });

    const asked = await request.get(`${roots.backend}/calendar/calendars/team-calendar/entries/${entryId}`, {
      headers: { authorization: inviteeCalls.token as string },
    });
    expect(asked.status(), await asked.text()).toBe(200);
    const after = await asked.json();
    expect(after.attendees.find((a: { displayName: string }) => a.displayName === registered.user.displayName)).toMatchObject({ response: 'NEEDS_ACTION' });
    // The organizer stays accepted: the move was theirs.
    expect(after.attendees.find((a: { mine?: boolean; response?: string }) => a.response === 'ACCEPTED')).toBeDefined();
  });
});
