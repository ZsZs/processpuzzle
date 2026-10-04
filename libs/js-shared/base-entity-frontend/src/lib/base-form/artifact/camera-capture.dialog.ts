import { AfterViewInit, Component, ElementRef, inject, OnDestroy, signal, viewChild } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatDialogActions, MatDialogContent, MatDialogRef, MatDialogTitle } from '@angular/material/dialog';
import { provideTranslocoScope, TranslocoService } from '@jsverse/transloco';

export type CameraCaptureMode = 'photo' | 'video';
type CaptureState = 'starting' | 'live' | 'recording' | 'review' | 'error';

/** Container formats in order of preference; the first the browser can record wins. Safari records only mp4. */
const VIDEO_MIME_CANDIDATES = ['video/webm;codecs=vp9,opus', 'video/webm', 'video/mp4'];
const PHOTO_MIME_TYPE = 'image/jpeg';
const PHOTO_QUALITY = 0.92;

/**
 * Takes a photo or records a video with the device camera and closes with the result as a `File` — or with
 * `undefined` when cancelled — so the artifact selector can treat it exactly like a picked file. Prefers the rear
 * camera where there is one. The stream is stopped whenever the dialog closes, so the camera light goes off.
 */
@Component({
  selector: 'app-camera-capture-dialog',
  standalone: true,
  imports: [MatDialogTitle, MatDialogContent, MatDialogActions, MatButton, MatButtonToggleGroup, MatButtonToggle],
  providers: [provideTranslocoScope({ scope: 'base_entity', alias: 'base_entity' })],
  template: `
    <h2 mat-dialog-title>{{ t('base_entity.camera_capture.title') }}</h2>
    <mat-dialog-content class="camera-capture">
      <mat-button-toggle-group
        [value]="mode()"
        (change)="switchMode($event.value)"
        [disabled]="state() === 'recording' || state() === 'review'"
        [attr.aria-label]="t('base_entity.camera_capture.mode')"
      >
        <mat-button-toggle value="photo" data-testid="camera-capture-mode-photo">{{ t('base_entity.camera_capture.photo') }}</mat-button-toggle>
        <mat-button-toggle value="video" data-testid="camera-capture-mode-video">{{ t('base_entity.camera_capture.video') }}</mat-button-toggle>
      </mat-button-toggle-group>

      @if (state() === 'error') {
        <p class="camera-capture__error" role="alert">{{ t('base_entity.camera_capture.unavailable') }}</p>
      }
      <video #preview class="camera-capture__media" autoplay playsinline muted [hidden]="state() === 'review' || state() === 'error'"></video>
      @if (state() === 'review' && capturedUrl(); as url) {
        @if (mode() === 'photo') {
          <img class="camera-capture__media" [src]="url" [alt]="t('base_entity.camera_capture.photo')" />
        } @else {
          <video class="camera-capture__media" [src]="url" controls playsinline></video>
        }
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button type="button" mat-button data-testid="camera-capture-cancel" (click)="cancel()">{{ t('base_entity.camera_capture.cancel') }}</button>
      @switch (state()) {
        @case ('live') {
          @if (mode() === 'photo') {
            <button type="button" mat-raised-button data-testid="camera-capture-take-photo" (click)="takePhoto()">{{ t('base_entity.camera_capture.take_photo') }}</button>
          } @else {
            <button type="button" mat-raised-button data-testid="camera-capture-start-recording" (click)="startRecording()">
              {{ t('base_entity.camera_capture.start_recording') }}
            </button>
          }
        }
        @case ('recording') {
          <button type="button" mat-raised-button color="warn" data-testid="camera-capture-stop-recording" (click)="stopRecording()">
            {{ t('base_entity.camera_capture.stop_recording') }}
          </button>
        }
        @case ('review') {
          <button type="button" mat-button data-testid="camera-capture-retake" (click)="retake()">{{ t('base_entity.camera_capture.retake') }}</button>
          <button type="button" mat-raised-button data-testid="camera-capture-use" (click)="use()">{{ t('base_entity.camera_capture.use') }}</button>
        }
      }
    </mat-dialog-actions>
  `,
  styles: [
    `
      .camera-capture {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 12px;
      }
      .camera-capture__media {
        width: 100%;
        max-width: 640px;
        max-height: 60vh;
        background: #000;
        border-radius: 4px;
      }
    `,
  ],
})
export class CameraCaptureDialog implements AfterViewInit, OnDestroy {
  private readonly dialogRef = inject<MatDialogRef<CameraCaptureDialog, File | undefined>>(MatDialogRef);
  private readonly translocoService = inject(TranslocoService);
  private readonly preview = viewChild.required<ElementRef<HTMLVideoElement>>('preview');

  readonly mode = signal<CameraCaptureMode>('photo');
  readonly state = signal<CaptureState>('starting');
  readonly capturedUrl = signal<string | null>(null);

  private stream: MediaStream | null = null;
  private recorder: MediaRecorder | null = null;
  private captured: File | null = null;

  ngAfterViewInit(): void {
    void this.startStream();
  }

  ngOnDestroy(): void {
    this.stopStream();
    this.discardCapture();
  }

  protected t(key: string): string {
    return this.translocoService.translate(key);
  }

  switchMode(mode: CameraCaptureMode): void {
    if (mode === this.mode()) return;
    this.mode.set(mode);
    // Only video needs the microphone, so the stream is renegotiated rather than asking for audio up front.
    void this.startStream();
  }

  takePhoto(): void {
    const video = this.preview().nativeElement;
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    canvas.getContext('2d')?.drawImage(video, 0, 0, canvas.width, canvas.height);
    canvas.toBlob(
      (blob) => {
        if (blob) this.review(new File([blob], this.fileName('photo', 'jpg'), { type: PHOTO_MIME_TYPE }));
      },
      PHOTO_MIME_TYPE,
      PHOTO_QUALITY,
    );
  }

  startRecording(): void {
    if (!this.stream) return;
    const mimeType = VIDEO_MIME_CANDIDATES.find((candidate) => MediaRecorder.isTypeSupported(candidate));
    const recorder = new MediaRecorder(this.stream, mimeType ? { mimeType } : undefined);
    const chunks: Blob[] = [];
    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) chunks.push(event.data);
    };
    recorder.onstop = () => {
      // The codecs parameter is dropped: the object store, and the icon table, key on the bare MIME type.
      const type = (recorder.mimeType || mimeType || 'video/webm').split(';')[0];
      this.review(new File(chunks, this.fileName('video', type === 'video/mp4' ? 'mp4' : 'webm'), { type }));
    };
    this.recorder = recorder;
    recorder.start();
    this.state.set('recording');
  }

  stopRecording(): void {
    this.recorder?.stop();
    this.recorder = null;
  }

  retake(): void {
    this.discardCapture();
    this.state.set(this.stream ? 'live' : 'starting');
  }

  use(): void {
    const file = this.captured;
    this.captured = null;
    this.dialogRef.close(file ?? undefined);
  }

  cancel(): void {
    if (this.recorder?.state === 'recording') {
      this.recorder.onstop = null;
      this.recorder.stop();
    }
    this.dialogRef.close(undefined);
  }

  private async startStream(): Promise<void> {
    this.stopStream();
    this.state.set('starting');
    const video: MediaTrackConstraints = { facingMode: { ideal: 'environment' } };
    try {
      this.stream = await this.openStream(video, this.mode() === 'video');
      this.preview().nativeElement.srcObject = this.stream;
      this.state.set('live');
    } catch {
      this.state.set('error');
    }
  }

  /** A video without sound is still worth recording, so a refused or missing microphone falls back to picture only. */
  private async openStream(video: MediaTrackConstraints, withAudio: boolean): Promise<MediaStream> {
    if (!withAudio) return navigator.mediaDevices.getUserMedia({ video });
    try {
      return await navigator.mediaDevices.getUserMedia({ video, audio: true });
    } catch {
      return navigator.mediaDevices.getUserMedia({ video });
    }
  }

  private stopStream(): void {
    this.stream?.getTracks().forEach((track) => track.stop());
    this.stream = null;
  }

  private review(file: File): void {
    this.discardCapture();
    this.captured = file;
    this.capturedUrl.set(URL.createObjectURL(file));
    this.state.set('review');
  }

  private discardCapture(): void {
    const url = this.capturedUrl();
    if (url) URL.revokeObjectURL(url);
    this.capturedUrl.set(null);
    this.captured = null;
  }

  private fileName(prefix: string, extension: string): string {
    return `${prefix}-${new Date().toISOString().replaceAll(/[:.]/g, '-')}.${extension}`;
  }
}
