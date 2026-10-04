import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { afterEach, assert, beforeEach, describe, expect, it, vi } from 'vitest';
import en from '../../../assets/i18n/base_ai/en.json';
import { Recognition } from '../../domain/recognition/recognition';
import { RecognitionService } from '../../domain/recognition/recognition.service';
import { RecognitionCameraComponent, RecognitionCandidateOption, RecognitionHit } from './recognition-camera.component';

const CANDIDATES: RecognitionCandidateOption[] = [
  { objectId: 'o-1', label: 'Vihar' },
  { objectId: 'o-2', label: 'Szellő' },
  { objectId: 'o-3', label: 'Hullám' },
];

const SHOT_AT = Date.UTC(2026, 9, 4, 10, 0, 0);

function answer(extra: Partial<Recognition>): Recognition {
  return { recognitionId: 'r-1', entityName: 'boat', status: 'DONE', candidates: [], candidatesWithoutGallery: [], createdAt: '', ...extra };
}

describe('RecognitionCameraComponent', () => {
  const recognize = vi.fn<RecognitionService['recognize']>();
  let fixture: ComponentFixture<RecognitionCameraComponent>;
  let hits: RecognitionHit[];

  async function render(candidates: readonly RecognitionCandidateOption[] = CANDIDATES): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      imports: [
        RecognitionCameraComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: { base_ai: en } },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
          preloadLangs: true,
        }),
      ],
      providers: [{ provide: RecognitionService, useValue: { recognize } }],
    });
    fixture = TestBed.createComponent(RecognitionCameraComponent);
    fixture.componentRef.setInput('entityName', 'boat');
    fixture.componentRef.setInput('candidates', candidates);
    hits = [];
    fixture.componentInstance.recognized.subscribe((hit) => hits.push(hit));
    fixture.detectChanges();
    // No camera in jsdom: after the first render the widget falls back to the device's photo picker.
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="frame-picker"]')).not.toBeNull();
    });
    return fixture.nativeElement as HTMLElement;
  }

  function pick(element: HTMLElement, files: File[] = [new File(['jpeg'], 'shot.jpg', { type: 'image/jpeg', lastModified: SHOT_AT })]): HTMLInputElement {
    const picker = element.querySelector<HTMLInputElement>('[data-testid="frame-picker"]');
    assert(picker, 'Frame picker should be rendered');
    Object.defineProperty(picker, 'files', { configurable: true, value: files });
    picker.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    return picker;
  }

  async function settle(probe: () => void): Promise<void> {
    await vi.waitFor(() => {
      fixture.detectChanges();
      probe();
    });
  }

  function click(element: HTMLElement, selector: string): void {
    const target = element.querySelector<HTMLButtonElement>(selector);
    assert(target, `Button ${selector} should be rendered`);
    target.click();
    fixture.detectChanges();
  }

  function choices(element: HTMLElement): HTMLButtonElement[] {
    return Array.from(element.querySelectorAll<HTMLButtonElement>('[data-testid="recognition-choice"]'));
  }

  // Braces matter: a function returned from beforeEach is run by Vitest as the test's teardown.
  beforeEach(() => {
    recognize.mockReset();
  });

  afterEach(() => fixture?.destroy());

  it('offers the photo picker instead of a live preview when there is no camera', async () => {
    const element = await render();
    expect(element.textContent).toContain(en.recognition_camera.noCamera);
    expect(element.querySelector('video')).toBeNull();
    expect(element.querySelector('[data-testid="shoot"]')).toBeNull();
    const picker = element.querySelector<HTMLInputElement>('[data-testid="frame-picker"]');
    const open = vi.spyOn(picker as HTMLInputElement, 'click');
    click(element, '[data-testid="pick-frames"]');
    expect(open).toHaveBeenCalledOnce();
  });

  it('disables the shot when there is no one to recognize', async () => {
    const element = await render([]);
    expect(element.querySelector<HTMLButtonElement>('[data-testid="pick-frames"]')?.disabled).toBe(true);
    expect(element.textContent).toContain(en.recognition_camera.noCandidates);
  });

  it('reports a certain match at once, as an automatic hit taken when the photo was', async () => {
    recognize.mockResolvedValue(answer({ outcome: 'MATCHED', objectId: 'o-2', score: 0.91, observedIdentifierText: 'HUN 12', candidates: [{ objectId: 'o-2', score: 0.91 }] }));
    const element = await render();
    const picker = pick(element);

    await settle(() => expect(element.querySelector('[data-testid="recognition-hit"]')).not.toBeNull());
    expect(recognize).toHaveBeenCalledWith('boat', [expect.any(File)], ['o-1', 'o-2', 'o-3'], expect.any(AbortSignal));
    expect(picker.value).toBe('');
    expect(hits).toEqual([{ recognitionId: 'r-1', objectId: 'o-2', score: 0.91, automatic: true, capturedAt: new Date(SHOT_AT).toISOString() }]);
    const hit = element.querySelector('[data-testid="recognition-hit"]')?.textContent;
    expect(hit).toContain('Szellő');
    expect(hit).toContain('91%');
    expect(hit).toContain(en.recognition_camera.automatic);
    expect(element.textContent).toContain('HUN 12');
    expect(element.querySelector('[data-testid="recognition-answer"]')?.getAttribute('data-outcome')).toBe('MATCHED');
    expect(choices(element)).toHaveLength(0);
  });

  it('lets a person overrule an automatic match; the choice supersedes it under the same recognition', async () => {
    recognize.mockResolvedValue(
      answer({
        recognitionId: 'r-7',
        outcome: 'MATCHED',
        objectId: 'o-1',
        score: 0.8,
        candidates: [
          { objectId: 'o-1', score: 0.8 },
          { objectId: 'o-3', score: 0.6 },
        ],
      }),
    );
    const element = await render();
    pick(element);
    await settle(() => expect(element.querySelector('[data-testid="overrule"]')).not.toBeNull());

    click(element, '[data-testid="overrule"]');
    expect(element.querySelector('[data-testid="overrule"]')).toBeNull();
    expect(choices(element).map((choice) => choice.textContent?.trim())).toEqual(['Vihar · 80%', 'Hullám · 60%']);

    choices(element)[1].click();
    fixture.detectChanges();

    expect(hits).toHaveLength(2);
    expect(hits[1]).toEqual({ recognitionId: 'r-7', objectId: 'o-3', score: 0.6, automatic: false, capturedAt: hits[0].capturedAt });
    expect(choices(element)).toHaveLength(0);
    expect(element.querySelector('[data-testid="recognition-hit"]')?.textContent).toContain(en.recognition_camera.chosen);
    expect(element.querySelector('[data-testid="overrule"]')).toBeNull();
  });

  it('asks who it is when uncertain, offering the top candidates by label', async () => {
    recognize.mockResolvedValue(
      answer({
        outcome: 'NEEDS_REVIEW',
        cropUrl: 'http://minio/crop.jpg',
        candidates: [
          { objectId: 'o-3', score: 0.55 },
          { objectId: 'o-1', score: 0.52 },
          { objectId: 'unknown', score: 0.2 },
        ],
      }),
    );
    const element = await render();
    pick(element);
    await settle(() => expect(choices(element)).toHaveLength(3));

    expect(hits).toEqual([]);
    expect(element.textContent).toContain(en.recognition_camera.whoIsIt);
    expect(element.querySelector('img')?.getAttribute('src')).toBe('http://minio/crop.jpg');
    // A candidate without a label is shown by its id.
    expect(choices(element).map((choice) => choice.textContent?.trim())).toEqual(['Hullám · 55%', 'Vihar · 52%', 'unknown · 20%']);

    choices(element)[1].click();
    fixture.detectChanges();
    expect(hits).toEqual([{ recognitionId: 'r-1', objectId: 'o-1', score: 0.52, automatic: false, capturedAt: new Date(SHOT_AT).toISOString() }]);
    expect(choices(element)).toHaveLength(0);
    expect(element.querySelector('[data-testid="recognition-hit"]')?.textContent).toContain('Vihar');
  });

  it('says so when nothing recognizable was in the shot', async () => {
    recognize.mockResolvedValue(answer({ outcome: 'NO_SUBJECT' }));
    const element = await render();
    pick(element);
    await settle(() => expect(element.textContent).toContain(en.recognition_camera.noSubject));
    expect(hits).toEqual([]);
    expect(choices(element)).toHaveLength(0);
    expect(element.querySelector('[data-testid="recognition-hit"]')).toBeNull();
  });

  it('shows a failed recognition as an alert, and the next shot clears it', async () => {
    recognize.mockResolvedValueOnce(answer({ status: 'FAILED', failureReason: 'vision server unavailable' }));
    recognize.mockResolvedValueOnce(answer({ outcome: 'NO_SUBJECT' }));
    const element = await render();
    pick(element);
    await settle(() => expect(element.querySelector('[role="alert"]')?.textContent).toBe('vision server unavailable'));
    expect(element.querySelector('[data-testid="recognition-answer"]')).toBeNull();
    expect(element.querySelector<HTMLButtonElement>('[data-testid="pick-frames"]')?.disabled).toBe(false);
    expect(hits).toEqual([]);

    pick(element);
    await settle(() => expect(element.textContent).toContain(en.recognition_camera.noSubject));
    expect(element.querySelector('[role="alert"]')).toBeNull();
  });

  it.each([
    [{ error: { errorText: 'No recognition profile for boat' } }, 'No recognition profile for boat'],
    [new Error('Network failed'), 'Network failed'],
    [answer({ status: 'FAILED' }), 'the recognition failed'],
  ])('shows an error as an alert (%j)', async (error, message) => {
    if ((error as Recognition).status === 'FAILED') recognize.mockResolvedValue(error as Recognition);
    else recognize.mockRejectedValue(error);
    const element = await render();
    pick(element);
    await settle(() => expect(element.querySelector('[role="alert"]')?.textContent).toBe(message));
  });

  it('shows progress and blocks a second shot while recognizing', async () => {
    let finish!: (recognition: Recognition) => void;
    recognize.mockImplementation(() => new Promise((resolve) => (finish = resolve)));
    const element = await render();
    pick(element);
    await settle(() => expect(element.textContent).toContain(en.recognition_camera.recognizing));
    expect(element.querySelector('mat-progress-bar')).not.toBeNull();
    expect(element.querySelector<HTMLButtonElement>('[data-testid="pick-frames"]')?.disabled).toBe(true);

    pick(element);
    expect(recognize).toHaveBeenCalledOnce();

    finish(answer({ outcome: 'NO_SUBJECT' }));
    await settle(() => expect(element.querySelector('mat-progress-bar')).toBeNull());
  });

  describe('with a camera', () => {
    const stop = vi.fn();
    const getUserMedia = vi.fn();

    beforeEach(() => {
      stop.mockReset();
      getUserMedia.mockReset().mockResolvedValue({ getTracks: () => [{ stop }] });
      vi.stubGlobal('isSecureContext', true);
      Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia } });
      // jsdom accepts only a real MediaStream there, which it cannot construct.
      Object.defineProperty(HTMLMediaElement.prototype, 'srcObject', { configurable: true, writable: true, value: null });
    });

    afterEach(() => {
      vi.unstubAllGlobals();
      delete (navigator as { mediaDevices?: unknown }).mediaDevices;
      if (srcObject) Object.defineProperty(HTMLMediaElement.prototype, 'srcObject', srcObject);
      else delete (HTMLMediaElement.prototype as { srcObject?: unknown }).srcObject;
    });

    const srcObject = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'srcObject');

    async function renderLive(): Promise<HTMLElement> {
      TestBed.configureTestingModule({
        imports: [RecognitionCameraComponent, TranslocoTestingModule.forRoot({ langs: { en: { base_ai: en } }, translocoConfig: { availableLangs: ['en'], defaultLang: 'en' }, preloadLangs: true })],
        providers: [{ provide: RecognitionService, useValue: { recognize } }],
      });
      fixture = TestBed.createComponent(RecognitionCameraComponent);
      fixture.componentRef.setInput('entityName', 'boat');
      fixture.componentRef.setInput('candidates', CANDIDATES);
      fixture.detectChanges();
      await vi.waitFor(() => expect(getUserMedia).toHaveBeenCalledOnce());
      return fixture.nativeElement as HTMLElement;
    }

    it('shows the live preview, and stops the camera when destroyed', async () => {
      const element = await renderLive();
      await settle(() => expect(element.querySelector<HTMLButtonElement>('[data-testid="shoot"]')?.disabled).toBe(false));
      expect(element.querySelector('video')?.srcObject).toBeTruthy();
      expect(element.querySelector('[data-testid="frame-picker"]')).toBeNull();
      fixture.destroy();
      expect(stop).toHaveBeenCalledOnce();
    });

    it('falls back to the photo picker when the camera is refused', async () => {
      getUserMedia.mockRejectedValue(new DOMException('denied', 'NotAllowedError'));
      const element = await renderLive();
      await settle(() => expect(element.querySelector('[data-testid="frame-picker"]')).not.toBeNull());
      expect(element.querySelector('video')).toBeNull();
    });

    it('reports a shot taken before the camera delivered a picture, without recognizing', async () => {
      const element = await renderLive();
      await settle(() => expect(element.querySelector<HTMLButtonElement>('[data-testid="shoot"]')?.disabled).toBe(false));
      click(element, '[data-testid="shoot"]');
      await settle(() => expect(element.querySelector('[role="alert"]')).not.toBeNull());
      expect(recognize).not.toHaveBeenCalled();
    });
  });

  it('offers Cancel only while a shot is under way or an answer is shown', async () => {
    const element = await render();
    expect(element.querySelector<HTMLButtonElement>('[data-testid="cancel"]')?.disabled).toBe(true);
  });

  it('abandons a recognition under way: the late answer is ignored and the next shot is possible', async () => {
    let finish: (answer: Recognition) => void = () => undefined;
    let signal: AbortSignal | undefined;
    recognize.mockImplementation((_entity, _frames, _candidates, abort) => {
      signal = abort;
      return new Promise<Recognition>((resolve) => (finish = resolve));
    });
    const element = await render();
    pick(element);
    await settle(() => expect(element.querySelector<HTMLButtonElement>('[data-testid="cancel"]')?.disabled).toBe(false));

    click(element, '[data-testid="cancel"]');
    expect(signal?.aborted).toBe(true);
    expect(element.querySelector('mat-progress-bar')).toBeNull();
    expect(element.querySelector<HTMLButtonElement>('[data-testid="pick-frames"]')?.disabled).toBe(false);

    finish(answer({ outcome: 'MATCHED', objectId: 'o-1', score: 0.9 }));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(hits).toEqual([]);
    expect(element.querySelector('[data-testid="recognition-answer"]')).toBeNull();
    expect(element.querySelector('[role="alert"]')).toBeNull();
  });

  it('shows no error for a shot abandoned while it was failing', async () => {
    recognize.mockImplementation((_entity, _frames, _candidates, abort) => new Promise<Recognition>((_resolve, reject) => abort?.addEventListener('abort', () => reject(abort.reason))));
    const element = await render();
    pick(element);
    await settle(() => expect(element.querySelector<HTMLButtonElement>('[data-testid="cancel"]')?.disabled).toBe(false));
    click(element, '[data-testid="cancel"]');
    await fixture.whenStable();
    fixture.detectChanges();
    expect(element.querySelector('[role="alert"]')).toBeNull();
  });

  it('dismisses an answer without reporting anything', async () => {
    recognize.mockResolvedValue(answer({ outcome: 'NEEDS_REVIEW', candidates: [{ objectId: 'o-1', score: 0.5 }] }));
    const element = await render();
    pick(element);
    await settle(() => expect(choices(element)).toHaveLength(1));

    click(element, '[data-testid="cancel"]');
    expect(element.querySelector('[data-testid="recognition-answer"]')).toBeNull();
    expect(hits).toEqual([]);
    expect(element.querySelector<HTMLButtonElement>('[data-testid="cancel"]')?.disabled).toBe(true);
  });

  it('ignores a cancelled selection and sends at most five frames', async () => {
    recognize.mockResolvedValue(answer({ outcome: 'NO_SUBJECT' }));
    const element = await render();
    pick(element, []);
    expect(recognize).not.toHaveBeenCalled();

    const files = Array.from({ length: 7 }, (_, i) => new File(['jpeg'], `f${i}.jpg`, { type: 'image/jpeg', lastModified: SHOT_AT + i * 1000 }));
    pick(element, files);
    await settle(() => expect(recognize).toHaveBeenCalledOnce());
    expect(recognize.mock.calls[0][1]).toEqual(files.slice(0, 5));
  });
});
