import { afterNextRender, Component, computed, DestroyRef, ElementRef, inject, input, numberAttribute, output, signal, viewChild } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatProgressBar } from '@angular/material/progress-bar';
import { TranslocoPipe } from '@jsverse/transloco';
import { RECOGNITION_CAMERA_I18N_SCOPE } from '../../base-ai.i18n';
import { Recognition, RECOGNITION_MAX_FRAMES } from '../../domain/recognition/recognition';
import { RecognitionService } from '../../domain/recognition/recognition.service';

/** One object that may be in front of the camera, with the label to show for it. */
export interface RecognitionCandidateOption {
  objectId: string;
  label: string;
}

/**
 * Who is in front of the camera, as the widget reports it.
 *
 * `automatic` is true when base-ai was certain on its own, false when a person chose among its suggestions.
 * A later hit with the same `recognitionId` supersedes an earlier one: it is a person correcting an
 * automatic match.
 */
export interface RecognitionHit {
  recognitionId: string;
  objectId: string;
  score?: number;
  automatic: boolean;
  /** When the shot was taken, on this device — not when the answer arrived. */
  capturedAt: string;
}

type CameraState = 'starting' | 'live' | 'unavailable';
type ShotState = 'ready' | 'capturing' | 'recognizing' | 'answered' | 'failed';

/**
 * The camera, and which of the given candidates it sees. The whole of base-ai's UI for recognition: point
 * the camera, take a shot, get the hit. Everything around it — what the candidates are, what a hit means,
 * where it is recorded — belongs to the page that hosts it, which hands in the `candidates` and listens to
 * `recognized`.
 *
 * A shot is {@link framesPerShot} frames grabbed {@link frameIntervalMs} apart, so that a moving subject is
 * seen from more than one angle. A certain match is reported at once; an uncertain one offers the top
 * candidates to choose from; a certain one can still be overruled. Cancel abandons a shot under way, or
 * dismisses an answer without reporting anything. Without a camera — no secure context, no
 * device, permission refused — the device's own photo picker takes its place.
 */
@Component({
  selector: 'pp-recognition-camera',
  standalone: true,
  imports: [MatButton, MatProgressBar, TranslocoPipe],
  template: `
    <div class="pp-recognition">
      @if (camera() === 'unavailable') {
        <p class="pp-recognition__note">{{ scope + '.noCamera' | transloco }}</p>
        <input #picker type="file" hidden multiple accept="image/*" capture="environment" (change)="onPicked(picker)" data-testid="frame-picker" />
      } @else {
        <video #preview class="pp-recognition__preview" autoplay playsinline muted></video>
      }
      <div class="pp-recognition__actions">
        @if (camera() === 'unavailable') {
          <button mat-flat-button type="button" [disabled]="busy() || candidates().length === 0" (click)="picker()?.nativeElement?.click()" data-testid="pick-frames">
            {{ scope + '.takePhoto' | transloco }}
          </button>
        } @else {
          <button mat-flat-button type="button" [disabled]="camera() !== 'live' || busy() || candidates().length === 0" (click)="shoot()" data-testid="shoot">
            {{ scope + '.shoot' | transloco }}
          </button>
        }
        <button mat-stroked-button type="button" [disabled]="!cancellable()" (click)="cancel()" data-testid="cancel">
          {{ scope + '.cancel' | transloco }}
        </button>
      </div>

      @if (candidates().length === 0) {
        <p class="pp-recognition__note">{{ scope + '.noCandidates' | transloco }}</p>
      }
      @if (busy()) {
        <div class="pp-recognition__progress">
          <span>{{ scope + '.' + shot() | transloco }}</span>
          <mat-progress-bar mode="indeterminate" />
        </div>
      }
      @if (error(); as message) {
        <p class="pp-recognition__error" role="alert">{{ message }}</p>
      }

      @if (shot() === 'answered' && recognition(); as answer) {
        <section class="pp-recognition__answer" [attr.data-outcome]="answer.outcome" data-testid="recognition-answer">
          @if (answer.cropUrl) {
            <img class="pp-recognition__crop" [src]="answer.cropUrl" [alt]="scope + '.cropAlt' | transloco" />
          }
          @if (answer.outcome === 'NO_SUBJECT') {
            <p>{{ scope + '.noSubject' | transloco }}</p>
          } @else if (chosen(); as hit) {
            <p class="pp-recognition__hit" data-testid="recognition-hit">
              <strong>{{ labelOf(hit.objectId) }}</strong>
              @if (hit.score !== undefined) {
                <span class="pp-recognition__score">{{ percent(hit.score) }}</span>
              }
              <span class="pp-recognition__note">{{ scope + (hit.automatic ? '.automatic' : '.chosen') | transloco }}</span>
            </p>
            @if (hit.automatic && !reviewing()) {
              <button mat-stroked-button type="button" (click)="reviewing.set(true)" data-testid="overrule">{{ scope + '.overrule' | transloco }}</button>
            }
          } @else {
            <p>{{ scope + '.whoIsIt' | transloco }}</p>
          }
          @if (answer.observedIdentifierText) {
            <p class="pp-recognition__note">{{ scope + '.read' | transloco }} <strong>{{ answer.observedIdentifierText }}</strong></p>
          }
          @if (reviewing() || (answer.outcome === 'NEEDS_REVIEW' && !chosen())) {
            <div class="pp-recognition__choices">
              @for (candidate of answer.candidates; track candidate.objectId) {
                <button mat-stroked-button type="button" (click)="choose(candidate.objectId, candidate.score)" data-testid="recognition-choice">
                  {{ labelOf(candidate.objectId) }} · {{ percent(candidate.score) }}
                </button>
              }
            </div>
          }
        </section>
      }
    </div>
  `,
  styles: [
    `
      .pp-recognition {
        display: flex;
        flex-direction: column;
        gap: 10px;
      }
      .pp-recognition__preview {
        width: 100%;
        max-height: 60vh;
        background: #000;
        border-radius: 4px;
      }
      .pp-recognition__actions {
        display: flex;
        gap: 8px;
      }
      .pp-recognition__progress {
        display: flex;
        flex-direction: column;
        gap: 4px;
        font-size: 12px;
      }
      .pp-recognition__answer {
        display: flex;
        flex-direction: column;
        gap: 8px;
        padding: 10px;
        border: 1px solid #cccccc;
        border-radius: 4px;
      }
      .pp-recognition__answer[data-outcome='MATCHED'] {
        border-color: var(--pp-color-light-green, rgb(92, 218, 207));
      }
      .pp-recognition__crop {
        max-width: 100%;
        max-height: 200px;
        object-fit: contain;
        align-self: flex-start;
      }
      .pp-recognition__hit {
        display: flex;
        align-items: baseline;
        flex-wrap: wrap;
        gap: 8px;
        margin: 0;
        font-size: 18px;
      }
      .pp-recognition__score {
        font-size: 13px;
      }
      .pp-recognition__choices {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
      }
      .pp-recognition__note {
        margin: 0;
        font-size: 12px;
        color: #666;
      }
      .pp-recognition__error {
        margin: 0;
        font-size: 12px;
        color: #d9534f;
      }
    `,
  ],
})
export class RecognitionCameraComponent {
  /** The entity type of the candidates — its definition code, which keys its recognition profile. */
  readonly entityName = input.required<string>();
  /** Who may be in front of the camera. The only constraint recognition works with. */
  readonly candidates = input.required<readonly RecognitionCandidateOption[]>();
  readonly framesPerShot = input(3, { transform: numberAttribute });
  readonly frameIntervalMs = input(250, { transform: numberAttribute });

  readonly recognized = output<RecognitionHit>();

  protected readonly scope = RECOGNITION_CAMERA_I18N_SCOPE;
  protected readonly camera = signal<CameraState>('starting');
  protected readonly shot = signal<ShotState>('ready');
  protected readonly recognition = signal<Recognition | undefined>(undefined);
  protected readonly chosen = signal<RecognitionHit | undefined>(undefined);
  protected readonly reviewing = signal(false);
  protected readonly error = signal<string | undefined>(undefined);
  protected readonly busy = computed(() => this.shot() === 'capturing' || this.shot() === 'recognizing');
  /** A shot under way can be abandoned, a shown answer dismissed. */
  protected readonly cancellable = computed(() => this.busy() || this.shot() === 'answered');

  private readonly preview = viewChild<ElementRef<HTMLVideoElement>>('preview');
  protected readonly picker = viewChild<ElementRef<HTMLInputElement>>('picker');
  private readonly service = inject(RecognitionService);
  private readonly labels = computed(() => new Map(this.candidates().map((candidate) => [candidate.objectId, candidate.label])));
  private stream: MediaStream | null = null;
  private inFlight: AbortController | null = null;

  constructor() {
    afterNextRender(() => void this.startCamera());
    inject(DestroyRef).onDestroy(() => {
      this.inFlight?.abort();
      this.stopCamera();
    });
  }

  /** Grabs the frames from the live preview and recognizes them. */
  protected async shoot(): Promise<void> {
    const video = this.preview()?.nativeElement;
    if (!video || this.busy()) return;
    const controller = this.begin();
    this.shot.set('capturing');
    const capturedAt = new Date().toISOString();
    try {
      const frames: Blob[] = [];
      const count = Math.max(1, Math.min(RECOGNITION_MAX_FRAMES, this.framesPerShot()));
      for (let index = 0; index < count; index++) {
        if (index > 0) await delay(this.frameIntervalMs());
        controller.signal.throwIfAborted();
        frames.push(await grab(video));
      }
      await this.recognize(frames, capturedAt, controller);
    } catch (error) {
      this.fail(error, controller);
    }
  }

  /** Abandons the shot under way — its answer, if one still arrives, is ignored — or dismisses the answer shown. */
  protected cancel(): void {
    this.inFlight?.abort();
    this.inFlight = null;
    this.recognition.set(undefined);
    this.chosen.set(undefined);
    this.reviewing.set(false);
    this.error.set(undefined);
    this.shot.set('ready');
  }

  /** The fallback without a live camera: the photos the device's picker returns. */
  protected async onPicked(picker: HTMLInputElement): Promise<void> {
    const frames = Array.from(picker.files ?? []).slice(0, RECOGNITION_MAX_FRAMES);
    picker.value = '';
    if (frames.length === 0 || this.busy()) return;
    const capturedAt = new Date(Math.min(...frames.map((frame) => frame.lastModified || Date.now()))).toISOString();
    const controller = this.begin();
    try {
      await this.recognize(frames, capturedAt, controller);
    } catch (error) {
      this.fail(error, controller);
    }
  }

  protected choose(objectId: string, score: number | undefined): void {
    const recognition = this.recognition();
    if (!recognition) return;
    this.reviewing.set(false);
    this.report({ recognitionId: recognition.recognitionId, objectId, score, automatic: false, capturedAt: this.capturedAt });
  }

  protected labelOf(objectId: string): string {
    return this.labels().get(objectId) ?? objectId;
  }

  protected percent(score: number): string {
    return `${Math.round(score * 100)}%`;
  }

  private capturedAt = '';

  /** A new shot supersedes whatever was under way. */
  private begin(): AbortController {
    this.inFlight?.abort();
    this.inFlight = new AbortController();
    return this.inFlight;
  }

  private async recognize(frames: Blob[], capturedAt: string, controller: AbortController): Promise<void> {
    this.error.set(undefined);
    this.chosen.set(undefined);
    this.reviewing.set(false);
    this.recognition.set(undefined);
    this.capturedAt = capturedAt;
    this.shot.set('recognizing');
    const recognition = await this.service.recognize(
      this.entityName(),
      frames,
      this.candidates().map((candidate) => candidate.objectId),
      controller.signal,
    );
    if (controller.signal.aborted) return;
    this.inFlight = null;
    if (recognition.status === 'FAILED') throw new Error(recognition.failureReason ?? 'the recognition failed');
    this.recognition.set(recognition);
    this.shot.set('answered');
    if (recognition.outcome === 'MATCHED' && recognition.objectId) {
      this.report({ recognitionId: recognition.recognitionId, objectId: recognition.objectId, score: recognition.score, automatic: true, capturedAt });
    }
  }

  private report(hit: RecognitionHit): void {
    this.chosen.set(hit);
    this.recognized.emit(hit);
  }

  /** An abandoned shot is no failure: {@link cancel} has already reset the widget. */
  private fail(error: unknown, controller: AbortController): void {
    if (controller.signal.aborted) return;
    this.inFlight = null;
    this.error.set(messageOf(error));
    this.shot.set('failed');
  }

  private async startCamera(): Promise<void> {
    const mediaDevices = globalThis.isSecureContext ? globalThis.navigator?.mediaDevices : undefined;
    if (typeof mediaDevices?.getUserMedia !== 'function') {
      this.camera.set('unavailable');
      return;
    }
    try {
      this.stream = await mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 } } });
      const video = this.preview()?.nativeElement;
      if (video) video.srcObject = this.stream;
      this.camera.set('live');
    } catch {
      this.camera.set('unavailable');
    }
  }

  private stopCamera(): void {
    this.stream?.getTracks().forEach((track) => track.stop());
    this.stream = null;
  }
}

/** The current video frame at its full resolution, as a JPEG. */
function grab(video: HTMLVideoElement): Promise<Blob> {
  const canvas = document.createElement('canvas');
  canvas.width = video.videoWidth;
  canvas.height = video.videoHeight;
  const context = canvas.getContext('2d');
  if (!context || canvas.width === 0) return Promise.reject(new Error('the camera has not delivered a picture yet'));
  context.drawImage(video, 0, 0);
  return new Promise((resolve, reject) =>
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('the frame could not be encoded'))), 'image/jpeg', 0.92),
  );
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** A readable message: the backend's `errorText` where there is one. */
function messageOf(error: unknown): string {
  const body = (error as { error?: { errorText?: string } })?.error;
  if (body?.errorText) return body.errorText;
  return error instanceof Error ? error.message : String(error);
}
