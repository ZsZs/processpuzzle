import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';
import { RecognitionCameraComponent, RecognitionHit } from '@processpuzzle/base-ai';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { RecognizeSampleComponent } from './recognize-sample.component';

const ROOT = 'http://backend/organizations/processpuzzle-testbed';
const BOATS = `${ROOT}/entities/boat`;
const RACES = `${ROOT}/entities/race`;
const REGISTRATIONS = `${ROOT}/entities/race-registration`;
const OBSERVATIONS = `${ROOT}/entities/race-observation`;

// Each test walks the page through several HTTP round trips and renders mat-selects; under the whole
// workspace's parallel test load that can exceed vitest's 5s default, so the budget is raised.
describe('base-ai RecognizeSampleComponent', { timeout: 20000 }, () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RecognizeSampleComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslocoTesting({ translations: { en: {} } }),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: ROOT } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  const next = (url: string, method = 'GET') => waitFor(() => http.match((request) => request.url === url && request.method === method)[0]);

  const flushObservations = async (observations: unknown[] = []): Promise<TestRequest> => {
    const request = await next(OBSERVATIONS);
    request.flush({ content: observations });
    return request;
  };

  /** Boots the page into the first race, round 1, START. */
  const render = async (observations: unknown[] = []) => {
    const fixture = TestBed.createComponent(RecognizeSampleComponent);
    (await next(BOATS)).flush({
      content: [
        { id: 'b-1', payload: { sailNumber: 'CAN 603', name: 'Maple Leaf' } },
        { id: 'b-2', payload: { sailNumber: 'GER 1234' } },
        { id: 'b-3', payload: { sailNumber: 'HUN 77' } },
      ],
    });
    (await next(RACES)).flush({
      content: [
        { id: 'r-1', payload: { name: 'Autumn Regatta', rounds: 3 } },
        { id: 'r-2', payload: { name: 'Club Championship', rounds: 2 } },
      ],
    });
    const registrations = await next(REGISTRATIONS);
    expect(registrations.request.params.get('rsql')).toBe('race==r-1');
    registrations.flush({
      content: [
        { id: 'g-1', payload: { race: 'r-1', boat: 'b-1', status: 'ENTERED' } },
        { id: 'g-2', payload: { race: 'r-1', boat: 'b-2', status: 'WITHDRAWN' } },
        { id: 'g-3', payload: { race: 'r-1', boat: 'b-3' } },
      ],
    });
    const observed = await flushObservations(observations);
    expect(observed.request.params.get('rsql')).toBe('race==r-1;round==1;checkpoint==START');
    await fixture.whenStable();
    fixture.detectChanges();
    const camera = fixture.debugElement.query(By.directive(RecognitionCameraComponent)).componentInstance as RecognitionCameraComponent;
    return { fixture, component: fixture.componentInstance as unknown as Record<string, (...args: unknown[]) => Promise<void>>, camera };
  };

  const hit: RecognitionHit = { recognitionId: 'rec-1', objectId: 'b-1', score: 0.91, automatic: true, capturedAt: '2026-10-10T10:00:00Z' };

  it('hands the camera the entries of the selected race only, withdrawn boats left out', async () => {
    const { camera } = await render();
    expect(camera.entityName()).toBe('boat');
    expect(camera.candidates()).toEqual([
      { objectId: 'b-1', label: 'CAN 603 Maple Leaf' },
      { objectId: 'b-3', label: 'HUN 77' },
    ]);
  });

  it('saves a hit as an observation of the selected context', async () => {
    const { fixture, camera } = await render();
    camera.recognized.emit(hit);

    const post = await next(OBSERVATIONS, 'POST');
    expect(post.request.body).toEqual({
      entityDefinitionCode: 'race-observation',
      payload: { race: 'r-1', round: 1, checkpoint: 'START', boat: 'b-1', capturedAt: hit.capturedAt, source: 'AUTOMATIC', score: 0.91, recognitionId: 'rec-1' },
    });
    post.flush({ id: 'o-1' });
    await flushObservations([{ id: 'o-1', payload: { ...post.request.body.payload } }]);
    await fixture.whenStable();
    fixture.detectChanges();

    const entries = fixture.nativeElement.querySelectorAll('[data-testid="entry"]') as NodeListOf<HTMLElement>;
    expect(entries[0].querySelector('[data-testid="tick"]')).toBeNull();
    expect(entries[1].querySelector('[data-testid="tick"]')).not.toBeNull();
  });

  it('replaces the observation of a recognition a person corrected', async () => {
    const { camera } = await render([{ id: 'o-1', payload: { race: 'r-1', boat: 'b-1', recognitionId: 'rec-1', source: 'AUTOMATIC', capturedAt: hit.capturedAt } }]);
    camera.recognized.emit({ ...hit, objectId: 'b-3', automatic: false });

    (await next(`${OBSERVATIONS}/o-1`, 'DELETE')).flush(null);
    const post = await next(OBSERVATIONS, 'POST');
    expect(post.request.body.payload).toMatchObject({ boat: 'b-3', source: 'MANUAL', recognitionId: 'rec-1' });
    post.flush({ id: 'o-2' });
    await flushObservations();
  });

  it('ticks a boat by hand into the same observation, without a recognition', async () => {
    const { fixture } = await render();
    (fixture.nativeElement.querySelector('[data-testid="tick"]') as HTMLButtonElement).click();

    const post = await next(OBSERVATIONS, 'POST');
    expect(post.request.body.payload).toMatchObject({ race: 'r-1', round: 1, checkpoint: 'START', boat: 'b-1', source: 'MANUAL' });
    expect(post.request.body.payload.recognitionId).toBeUndefined();
    post.flush({ id: 'o-1' });
    await flushObservations();
  });

  it('follows the context: another checkpoint, round or race reads its own observations and entries', async () => {
    const { component } = await render();

    void component['selectCheckpoint']('FINISH');
    expect((await flushObservations()).request.params.get('rsql')).toBe('race==r-1;round==1;checkpoint==FINISH');

    void component['selectRound'](2);
    expect((await flushObservations()).request.params.get('rsql')).toBe('race==r-1;round==2;checkpoint==FINISH');

    void component['selectRace']('r-2');
    const registrations = await next(REGISTRATIONS);
    expect(registrations.request.params.get('rsql')).toBe('race==r-2');
    registrations.flush({ content: [] });
    expect((await flushObservations()).request.params.get('rsql')).toBe('race==r-2;round==1;checkpoint==FINISH');
  });

  it('shows the backend refusal when a save fails', async () => {
    const { fixture, camera } = await render();
    camera.recognized.emit(hit);
    (await next(OBSERVATIONS, 'POST')).flush({ errorText: 'capturedAt is required' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('capturedAt is required');
  });
});

async function waitFor<T>(probe: () => T | undefined): Promise<T> {
  for (let i = 0; i < 400; i++) {
    const found = probe();
    if (found) return found;
    await new Promise((resolve) => setTimeout(resolve, 10));
  }
  throw new Error('request never sent');
}
