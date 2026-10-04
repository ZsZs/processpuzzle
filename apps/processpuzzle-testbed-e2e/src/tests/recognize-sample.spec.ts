import { expect, Page, Request, test } from '@playwright/test';
import { RecognizePage } from '../pages/recognize-page';
import { EntityApi, EntityObject, serviceRoots } from '../support/race-sample.api';

/**
 * The race sample's checkpoint screen against the real backend — races, registrations and observations are
 * base-entity objects, created and checked through its REST API — with base-ai's *answer* canned. That keeps
 * the suite independent of the vision server, which the default stacks do not run, and of how well a test
 * pattern resembles a boat; `recognize-sample.ai.spec.ts` is the opt-in round trip through the real models.
 *
 * Chromium's fake camera stands in for a real one: the widget gets a live preview and grabs real frames from
 * it, so Recognize and the frame grab are exercised as on a phone. Chromium only — the flags have no WebKit
 * or Firefox equivalent.
 */
test.use({
  permissions: ['camera'],
  launchOptions: { args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'] },
});

const BOATS = { can: 'CAN 603', ger: 'GER 1234', hun: 'HUN 77' } as const;
const LABELS = { can: 'CAN 603 Maple Leaf', ger: 'GER 1234 Nordwind', hun: 'HUN 77 Balaton' } as const;

interface ObservationPayload {
  race: string;
  round: number;
  checkpoint: string;
  boat: string;
  capturedAt: string;
  source: string;
  score?: number;
  recognitionId?: string;
}

/** What the canned AI answers a recognition with: a function of the request's candidates. */
type Answer = (candidateObjectIds: string[]) => Record<string, unknown>;

test.describe('[Race sample] RECOGNIZE', () => {
  // Serial because the tests share this run's races; each one still asserts only on its own checkpoint.
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let api: EntityApi;
  let boats: Record<keyof typeof BOATS, string>;
  let regatta: EntityObject;
  let championship: EntityObject;
  const regattaName = `E2E Regatta ${Date.now()}`;
  const championshipName = `E2E Championship ${Date.now()}`;

  test.beforeAll(async ({ playwright, baseURL }) => {
    const request = await playwright.request.newContext();
    api = new EntityApi(request, await serviceRoots(request, baseURL as string));
    boats = {
      can: (await api.findBy('boat', 'sailNumber', BOATS.can)).id,
      ger: (await api.findBy('boat', 'sailNumber', BOATS.ger)).id,
      hun: (await api.findBy('boat', 'sailNumber', BOATS.hun)).id,
    };
    // Races of this run only, so that nothing here reads or writes another run's — or a person's — sightings.
    regatta = await api.create('race', { name: regattaName, rounds: 2 });
    championship = await api.create('race', { name: championshipName, rounds: 1 });
    await api.create('race-registration', { race: regatta.id, boat: boats.can, helm: 'e2e', status: 'ENTERED' });
    await api.create('race-registration', { race: regatta.id, boat: boats.ger, helm: 'e2e', status: 'ENTERED' });
    await api.create('race-registration', { race: regatta.id, boat: boats.hun, helm: 'e2e', status: 'WITHDRAWN' });
    await api.create('race-registration', { race: championship.id, boat: boats.hun, helm: 'e2e', status: 'ENTERED' });
  });

  test.afterAll(async () => {
    const observations = [...(await observationsOf(regatta.id)), ...(await observationsOf(championship.id))];
    await api.removeAll(observations.map((observation) => ({ code: 'race-observation', id: observation.id })));
  });

  const observationsOf = (raceId: string) => api.list<ObservationPayload>('race-observation', `race==${raceId}`);

  async function open(page: Page, answer: Answer): Promise<{ recognize: RecognizePage; requests: Request[] }> {
    const requests = await cannedRecognition(page, answer);
    const recognize = new RecognizePage(page);
    await recognize.goto();
    await recognize.selectRace(regattaName);
    await expect(recognize.shoot).toBeEnabled();
    return { recognize, requests };
  }

  test('hands the camera only the entries of the selected race', async ({ page }) => {
    const { recognize, requests } = await open(page, () => ({ outcome: 'NO_SUBJECT' }));

    await expect(recognize.entries).toHaveCount(2);
    await expect(recognize.entry(LABELS.can)).toBeVisible();
    await expect(recognize.entry(LABELS.ger)).toBeVisible();
    await expect(recognize.entry(LABELS.hun), 'a withdrawn boat is no candidate').toHaveCount(0);

    await recognize.shoot.click();
    await expect(recognize.answer).toHaveAttribute('data-outcome', 'NO_SUBJECT');
    expect(candidatesOf(requests[0]).sort()).toEqual([boats.can, boats.ger].sort());

    await recognize.selectRace(championshipName);
    await expect(recognize.entries).toHaveCount(1);
    await expect(recognize.entry(LABELS.hun)).toBeVisible();
    await recognize.shoot.click();
    await expect.poll(() => requests.length).toBe(2);
    expect(candidatesOf(requests[1])).toEqual([boats.hun]);
  });

  test('saves a certain match as an automatic observation of the selected round and checkpoint', async ({ page }) => {
    const { recognize, requests } = await open(page, () => matched(boats.can, 0.93));
    await recognize.selectRound(2);
    await recognize.selectCheckpoint('Finish');

    await recognize.shoot.click();
    await expect(recognize.hit).toContainText(LABELS.can);
    await expect(recognize.entry(LABELS.can)).toContainText('camera');
    await expect(recognize.tick(LABELS.ger)).toBeVisible();

    const saved = (await observationsOf(regatta.id)).filter((observation) => observation.payload.checkpoint === 'FINISH');
    expect(saved).toHaveLength(1);
    expect(saved[0].payload).toMatchObject({ race: regatta.id, round: 2, boat: boats.can, source: 'AUTOMATIC', score: 0.93, recognitionId: 'e2e-r-1' });
    // The shot's time on the device, taken before the answer arrived.
    expect(Date.parse(saved[0].payload.capturedAt)).toBeLessThanOrEqual(Date.now());
    expect(requests).toHaveLength(1);
  });

  test('lets a person choose when the camera is not sure, and records the choice as manual', async ({ page }) => {
    const { recognize } = await open(page, () => ({
      outcome: 'NEEDS_REVIEW',
      candidates: [
        { objectId: boats.ger, score: 0.62 },
        { objectId: boats.can, score: 0.55 },
      ],
    }));
    await recognize.selectCheckpoint('Pre-start (on the water)');

    await recognize.shoot.click();
    await expect(recognize.choices).toHaveCount(2);
    await recognize.choices.filter({ hasText: LABELS.ger }).click();
    await expect(recognize.entry(LABELS.ger)).toContainText('by hand');

    const saved = (await observationsOf(regatta.id)).filter((observation) => observation.payload.checkpoint === 'PRE_START');
    expect(saved.map((observation) => observation.payload)).toEqual([expect.objectContaining({ round: 1, boat: boats.ger, source: 'MANUAL', recognitionId: 'e2e-r-1' })]);
  });

  test('ticks a boat the camera missed by hand, without any recognition', async ({ page }) => {
    const { recognize, requests } = await open(page, () => ({ outcome: 'NO_SUBJECT' }));
    await recognize.selectCheckpoint('Start');

    await recognize.tick(LABELS.ger).click();
    await expect(recognize.entry(LABELS.ger)).toContainText('by hand');

    const saved = (await observationsOf(regatta.id)).filter((observation) => observation.payload.checkpoint === 'START');
    expect(saved).toHaveLength(1);
    expect(saved[0].payload).toMatchObject({ round: 1, boat: boats.ger, source: 'MANUAL' });
    expect(saved[0].payload.recognitionId).toBeUndefined();
    expect(requests, 'a hand tick asks the AI nothing').toHaveLength(0);
  });

  test('shows the sightings of the selected context only', async ({ page }) => {
    await api.create('race-observation', {
      race: championship.id,
      round: 1,
      checkpoint: 'PRE_START',
      boat: boats.hun,
      capturedAt: new Date().toISOString(),
      source: 'MANUAL',
    });
    const { recognize } = await open(page, () => ({ outcome: 'NO_SUBJECT' }));
    await recognize.selectRace(championshipName);

    await recognize.selectCheckpoint('Pre-start (on the water)');
    await expect(recognize.entry(LABELS.hun)).toContainText('by hand');
    await expect(recognize.tick(LABELS.hun)).toHaveCount(0);

    await recognize.selectCheckpoint('Finish');
    await expect(recognize.tick(LABELS.hun)).toBeVisible();

    await recognize.selectRace(regattaName);
    await expect(recognize.entry(LABELS.hun), 'the other race has its own entries').toHaveCount(0);
  });

  test('cancels a recognition under way: nothing is recorded and the next shot is possible', async ({ page }) => {
    const { recognize, requests } = await open(page, () => ({ status: 'QUEUED' }));
    await recognize.selectCheckpoint('Finish');
    const before = (await observationsOf(regatta.id)).length;

    await recognize.shoot.click();
    await expect.poll(() => requests.length).toBe(1);
    await expect(recognize.cancel).toBeEnabled();
    await recognize.cancel.click();

    await expect(recognize.camera.locator('mat-progress-bar')).toHaveCount(0);
    await expect(recognize.shoot).toBeEnabled();
    await expect(recognize.cancel).toBeDisabled();
    expect(await observationsOf(regatta.id)).toHaveLength(before);
  });
});

function matched(objectId: string, score: number): Record<string, unknown> {
  return { outcome: 'MATCHED', objectId, score, candidates: [{ objectId, score }] };
}

function candidatesOf(request: Request): string[] {
  return (request.postDataJSON() as { candidateObjectIds: string[] }).candidateObjectIds;
}

/**
 * Stands in for base-ai: upload slots that accept any PUT, and recognitions answered by `answer`. A QUEUED
 * answer stays queued on every poll. Returns the recognition requests the page made, for their candidates.
 */
async function cannedRecognition(page: Page, answer: Answer): Promise<Request[]> {
  const requests: Request[] = [];
  let slots = 0;
  await page.route('**/media-uploads', (route) =>
    route.fulfill({
      status: 201,
      json: { mediaKey: `e2e-m-${++slots}`, uploadUrl: `${new URL(route.request().url()).origin}/__e2e-frame/${slots}`, requiredHeaders: {}, expiresAt: '' },
    }),
  );
  await page.route('**/__e2e-frame/**', (route) => route.fulfill({ status: 200, body: '' }));
  await page.route('**/recognitions', (route) => {
    requests.push(route.request());
    const body = answer(candidatesOf(route.request()));
    return route.fulfill({ status: 202, json: recognition(body) });
  });
  await page.route('**/recognitions/*', (route) => route.fulfill({ status: 200, json: recognition({ status: 'QUEUED' }) }));
  return requests;
}

function recognition(extra: Record<string, unknown>): Record<string, unknown> {
  return { recognitionId: 'e2e-r-1', entityName: 'boat', status: 'DONE', candidates: [], candidatesWithoutGallery: [], createdAt: new Date().toISOString(), ...extra };
}
