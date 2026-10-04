import { ANIMATION_MODULE_TYPE } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { TranslocoService } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CameraCaptureDialog } from './camera-capture.dialog';

class FakeMediaRecorder {
  static readonly isTypeSupported = vi.fn((type: string) => type === 'video/webm');
  static last: FakeMediaRecorder | null = null;
  state: 'inactive' | 'recording' = 'inactive';
  ondataavailable: ((event: { data: Blob }) => void) | null = null;
  onstop: (() => void) | null = null;
  readonly mimeType: string;

  constructor(
    readonly stream: MediaStream,
    options?: { mimeType?: string },
  ) {
    this.mimeType = options?.mimeType ?? '';
    FakeMediaRecorder.last = this;
  }

  start(): void {
    this.state = 'recording';
  }

  stop(): void {
    this.state = 'inactive';
    this.ondataavailable?.({ data: new Blob(['frame'], { type: this.mimeType }) });
    this.onstop?.();
  }
}

function fakeStream(): MediaStream & { track: { stop: ReturnType<typeof vi.fn> } } {
  const track = { stop: vi.fn() };
  return { track, getTracks: () => [track] } as unknown as MediaStream & { track: { stop: ReturnType<typeof vi.fn> } };
}

describe('CameraCaptureDialog', () => {
  let fixture: ComponentFixture<CameraCaptureDialog>;
  let component: CameraCaptureDialog;
  let dialogRef: { close: ReturnType<typeof vi.fn> };
  let getUserMedia: ReturnType<typeof vi.fn>;
  let stream: ReturnType<typeof fakeStream>;

  const host = () => fixture.nativeElement as HTMLElement;
  const button = (testId: string) => host().querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`);

  async function create(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [CameraCaptureDialog],
      providers: [
        { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: TranslocoService, useValue: { translate: vi.fn((key: string) => key) } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CameraCaptureDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await vi.waitFor(() => expect(component.state()).not.toBe('starting'));
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    dialogRef = { close: vi.fn() };
    stream = fakeStream();
    getUserMedia = vi.fn().mockResolvedValue(stream);
    Object.defineProperty(globalThis.navigator, 'mediaDevices', { value: { getUserMedia }, configurable: true });
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder);
    // happy-dom refuses anything but its own MediaStream here.
    vi.spyOn(HTMLMediaElement.prototype, 'srcObject', 'set').mockImplementation(() => undefined);
    // jsdom implements neither, so they are assigned rather than spied on.
    URL.createObjectURL = vi.fn(() => 'blob:capture');
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ drawImage: vi.fn() } as unknown as CanvasRenderingContext2D);
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((callback: BlobCallback, type?: string) => callback(new Blob(['jpeg'], { type })));
  });

  afterEach(() => {
    fixture?.destroy();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    Object.defineProperty(globalThis.navigator, 'mediaDevices', { value: undefined, configurable: true });
  });

  it('opens the rear camera without the microphone in photo mode', async () => {
    await create();

    expect(getUserMedia).toHaveBeenCalledWith({ video: { facingMode: { ideal: 'environment' } } });
    expect(component.state()).toBe('live');
    expect(button('camera-capture-take-photo')).not.toBeNull();
  });

  it('reports a camera that cannot be started', async () => {
    getUserMedia.mockRejectedValue(new Error('NotAllowedError'));

    await create();

    expect(component.state()).toBe('error');
    expect(host().querySelector('[role="alert"]')?.textContent).toContain('base_entity.camera_capture.unavailable');
  });

  it('takes a JPEG photo, reviews it and closes with the file', async () => {
    await create();

    component.takePhoto();
    fixture.detectChanges();

    expect(component.state()).toBe('review');
    expect(host().querySelector('img')?.getAttribute('src')).toBe('blob:capture');
    button('camera-capture-use')?.click();
    const file = dialogRef.close.mock.calls[0][0] as File;
    expect(file.type).toBe('image/jpeg');
    expect(file.name).toMatch(/^photo-.*\.jpg$/);
  });

  it('discards the capture on retake and returns to the live preview', async () => {
    await create();
    component.takePhoto();

    component.retake();

    expect(component.state()).toBe('live');
    expect(component.capturedUrl()).toBeNull();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:capture');
  });

  it('asks for the microphone in video mode and records a webm video', async () => {
    await create();

    component.switchMode('video');
    await vi.waitFor(() => expect(component.state()).toBe('live'));
    expect(getUserMedia).toHaveBeenLastCalledWith({ video: { facingMode: { ideal: 'environment' } }, audio: true });
    expect(stream.track.stop).toHaveBeenCalled();

    component.startRecording();
    expect(component.state()).toBe('recording');
    component.stopRecording();
    component.use();

    const file = dialogRef.close.mock.calls[0][0] as File;
    expect(file.type).toBe('video/webm');
    expect(file.name).toMatch(/^video-.*\.webm$/);
  });

  it('records without sound when the microphone is refused', async () => {
    await create();
    getUserMedia.mockRejectedValueOnce(new Error('NotAllowedError'));

    component.switchMode('video');

    await vi.waitFor(() => expect(component.state()).toBe('live'));
    expect(getUserMedia).toHaveBeenLastCalledWith({ video: { facingMode: { ideal: 'environment' } } });
  });

  it('ignores a switch to the mode already selected', async () => {
    await create();

    component.switchMode('photo');

    expect(getUserMedia).toHaveBeenCalledTimes(1);
  });

  it('closes empty-handed on cancel, discarding a recording in progress', async () => {
    await create();
    component.switchMode('video');
    await vi.waitFor(() => expect(component.state()).toBe('live'));
    component.startRecording();

    component.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith(undefined);
    expect(component.state()).toBe('recording');
    expect(FakeMediaRecorder.last?.state).toBe('inactive');
  });

  it('stops the camera when the dialog is destroyed', async () => {
    await create();

    fixture.destroy();

    expect(stream.track.stop).toHaveBeenCalled();
  });
});
