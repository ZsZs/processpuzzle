import { DestroyRef, inject, Injectable, signal } from '@angular/core';

/**
 * Whether this device can offer a camera capture at all. Three things must hold: a secure context (browsers
 * withhold `mediaDevices` elsewhere), `getUserMedia`, and at least one `videoinput` device. Device kinds are
 * listed before any permission is granted — only their labels are withheld — so the check asks the user nothing.
 * It is re-run on `devicechange`, so plugging in a webcam reveals the camera option without a reload.
 */
@Injectable({ providedIn: 'root' })
export class CameraSupportService {
  private readonly availableSignal = signal(false);
  readonly available = this.availableSignal.asReadonly();

  constructor() {
    const mediaDevices = this.mediaDevices();
    if (!mediaDevices) return;

    const onDeviceChange = () => void this.refresh();
    mediaDevices.addEventListener?.('devicechange', onDeviceChange);
    inject(DestroyRef).onDestroy(() => mediaDevices.removeEventListener?.('devicechange', onDeviceChange));
    void this.refresh();
  }

  async refresh(): Promise<boolean> {
    const mediaDevices = this.mediaDevices();
    let available = false;
    if (mediaDevices?.enumerateDevices) {
      try {
        const devices = await mediaDevices.enumerateDevices();
        available = devices.some((device) => device.kind === 'videoinput');
      } catch {
        available = false;
      }
    }
    this.availableSignal.set(available);
    return available;
  }

  private mediaDevices(): MediaDevices | undefined {
    if (!globalThis.isSecureContext) return undefined;
    const mediaDevices = globalThis.navigator?.mediaDevices;
    return typeof mediaDevices?.getUserMedia === 'function' ? mediaDevices : undefined;
  }
}
