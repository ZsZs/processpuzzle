import { expect, test } from '@playwright/test';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { RecognizePage } from '../pages/recognize-page';
import { Artifact, deleteArtifact, EntityApi, EntityObject, serviceRoots, uploadArtifact } from '../support/race-sample.api';

/**
 * The whole recognition round trip, through the real vision server: a boat is saved with a photo in its
 * ARTIFACT attribute, base-ai enrolls it on its own, and the same photo, handed to the Recognize page,
 * comes back as that boat — recorded as an automatic observation.
 *
 * **Opt-in.** Runs only with `E2E_AI=1`, on a stack started with `COMPOSE_PROFILES=ai`: the default local
 * and CI stacks run no vision server, and on CPU an enrollment and a recognition take tens of seconds each.
 *
 * No camera is granted, so the widget falls back to the photo picker, which works in every browser; the
 * camera path itself is covered by `recognize-sample.spec.ts`. The photo is the sample's CAN 603 from
 * Wikimedia Commons (CC BY-SA 3.0, Noahsachs), downloaded on first use into `tmp/` rather than committed.
 */
test.skip(!process.env['E2E_AI'], 'needs the vision server: run with E2E_AI=1 on a stack with COMPOSE_PROFILES=ai');

const PHOTO_URL = 'https://upload.wikimedia.org/wikipedia/commons/3/3d/49er_sail_boats.jpg';
const PHOTO_PATH = path.join(__dirname, '../../tmp/fixtures/49er_sail_boats.jpg');
/** A cold vision server loads three models before the first photo; then OCR dominates, ~15 s a photo. */
const ENROLLMENT_TIMEOUT_MS = 240_000;
const RECOGNITION_TIMEOUT_MS = 180_000;

test.describe('[Race sample] RECOGNIZE @ai', () => {
  test.describe.configure({ timeout: 600_000 });

  let api: EntityApi;
  let photo: Artifact | undefined;
  let boat: EntityObject;
  let race: EntityObject | undefined;
  let cleanup: () => Promise<void> = async () => undefined;
  const boatName = `E2E Maple Leaf ${Date.now()}`;
  const raceName = `E2E AI Regatta ${Date.now()}`;

  test.beforeAll(async ({ playwright, baseURL }) => {
    test.setTimeout(ENROLLMENT_TIMEOUT_MS + 60_000);
    const request = await playwright.request.newContext();
    const roots = await serviceRoots(request, baseURL as string);
    api = new EntityApi(request, roots);
    // Registered first, so that a setup failing halfway still removes what it had created.
    cleanup = async () => {
      const observations = race ? await api.list('race-observation', `race==${race.id}`) : [];
      await api.removeAll(observations.map((observation) => ({ code: 'race-observation', id: observation.id })));
      if (photo) await deleteArtifact(request, roots, photo);
    };

    photo = await uploadArtifact(request, roots, '49er_sail_boats.jpg', 'image/jpeg', await samplePhoto());
    boat = await api.create('boat', { sailNumber: 'CAN 603', name: boatName, boatClass: '49ER', photos: [photo] });
    race = await api.create('race', { name: raceName, rounds: 1 });
    await api.create('race-registration', { race: race.id, boat: boat.id, helm: 'e2e', status: 'ENTERED' });
    // A second entry without photos: recognition has to tell the two apart, by appearance and sail number.
    const other = await api.findBy('boat', 'sailNumber', 'GER 1234');
    await api.create('race-registration', { race: race.id, boat: other.id, helm: 'e2e', status: 'ENTERED' });

    // Saving the boat is all it takes: base-ai hears of it and enrolls the photo in the background.
    await expect
      .poll(
        async () => {
          const response = await request.get(`${roots.backend}/entities/boat/${boat.id}/enrollment`);
          return response.ok() ? (await response.json()).status : `HTTP ${response.status()}`;
        },
        { timeout: ENROLLMENT_TIMEOUT_MS, intervals: [3_000] },
      )
      .toBe('READY');
  });

  test.afterAll(async () => cleanup());

  test('recognizes the enrolled boat among the race entries and records it automatically', async ({ page }) => {
    const recognize = new RecognizePage(page);
    await recognize.goto();
    await recognize.selectRace(raceName);
    await recognize.selectCheckpoint('Finish');
    await expect(recognize.pickFrames, 'no camera is granted, so the picker stands in').toBeEnabled();

    await recognize.framePicker.setInputFiles(PHOTO_PATH);

    const label = `CAN 603 ${boatName}`;
    await expect(recognize.answer).toHaveAttribute('data-outcome', 'MATCHED', { timeout: RECOGNITION_TIMEOUT_MS });
    await expect(recognize.hit).toContainText(label);
    await expect(recognize.entry(label)).toContainText('camera');

    const saved = await api.list<{ boat: string; source: string; checkpoint: string; score: number }>('race-observation', `race==${race?.id}`);
    expect(saved.map((observation) => observation.payload)).toEqual([
      expect.objectContaining({ boat: boat.id, source: 'AUTOMATIC', checkpoint: 'FINISH', score: expect.any(Number) }),
    ]);
  });
});

/** The sample photo, downloaded once and kept in `tmp/` (git-ignored). Wikimedia asks for a descriptive User-Agent. */
async function samplePhoto(): Promise<Buffer> {
  if (!fs.existsSync(PHOTO_PATH)) {
    const response = await fetch(PHOTO_URL, { headers: { 'User-Agent': 'ProcessPuzzle-e2e/1.0 (https://github.com/ZsZs/processpuzzle)' } });
    expect(response.ok, `downloading the sample photo: HTTP ${response.status}`).toBe(true);
    fs.mkdirSync(path.dirname(PHOTO_PATH), { recursive: true });
    fs.writeFileSync(PHOTO_PATH, Buffer.from(await response.arrayBuffer()));
  }
  return fs.readFileSync(PHOTO_PATH);
}
