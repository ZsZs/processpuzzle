import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CameraSupportService } from './camera-support.service';

function device(kind: MediaDeviceKind): MediaDeviceInfo {
  return { kind, deviceId: kind, groupId: '', label: '', toJSON: () => ({}) } as MediaDeviceInfo;
}

describe('CameraSupportService', () => {
  let mediaDevices: {
    getUserMedia?: ReturnType<typeof vi.fn>;
    enumerateDevices: ReturnType<typeof vi.fn>;
    addEventListener: ReturnType<typeof vi.fn>;
    removeEventListener: ReturnType<typeof vi.fn>;
  };

  function stubEnvironment(secure: boolean): void {
    vi.stubGlobal('isSecureContext', secure);
    Object.defineProperty(globalThis.navigator, 'mediaDevices', { value: mediaDevices, configurable: true });
  }

  async function createService(): Promise<CameraSupportService> {
    TestBed.resetTestingModule();
    const service = TestBed.inject(CameraSupportService);
    await vi.waitFor(() => expect(mediaDevices.enumerateDevices).toHaveBeenCalled());
    await Promise.resolve();
    return service;
  }

  beforeEach(() => {
    mediaDevices = {
      getUserMedia: vi.fn(),
      enumerateDevices: vi.fn().mockResolvedValue([device('audioinput'), device('videoinput')]),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    };
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    Object.defineProperty(globalThis.navigator, 'mediaDevices', { value: undefined, configurable: true });
  });

  it('reports a camera when a secure context lists a videoinput device', async () => {
    stubEnvironment(true);

    const service = await createService();

    expect(service.available()).toBe(true);
  });

  it('reports no camera when only audio devices are listed', async () => {
    mediaDevices.enumerateDevices.mockResolvedValue([device('audioinput'), device('audiooutput')]);
    stubEnvironment(true);

    const service = await createService();

    expect(service.available()).toBe(false);
  });

  it('reports no camera when the device listing fails', async () => {
    mediaDevices.enumerateDevices.mockRejectedValue(new Error('denied'));
    stubEnvironment(true);

    const service = await createService();

    expect(service.available()).toBe(false);
  });

  it('never asks for devices outside a secure context', async () => {
    stubEnvironment(false);
    TestBed.resetTestingModule();

    const service = TestBed.inject(CameraSupportService);

    expect(await service.refresh()).toBe(false);
    expect(mediaDevices.enumerateDevices).not.toHaveBeenCalled();
  });

  it('never asks for devices when getUserMedia is missing', async () => {
    delete mediaDevices.getUserMedia;
    stubEnvironment(true);
    TestBed.resetTestingModule();

    const service = TestBed.inject(CameraSupportService);

    expect(await service.refresh()).toBe(false);
    expect(mediaDevices.enumerateDevices).not.toHaveBeenCalled();
  });

  it('re-checks when a device is plugged in, and stops listening on destroy', async () => {
    mediaDevices.enumerateDevices.mockResolvedValueOnce([]);
    stubEnvironment(true);
    const service = await createService();
    expect(service.available()).toBe(false);

    const onDeviceChange = mediaDevices.addEventListener.mock.calls[0][1] as () => void;
    onDeviceChange();
    await vi.waitFor(() => expect(service.available()).toBe(true));

    TestBed.resetTestingModule();
    expect(mediaDevices.removeEventListener).toHaveBeenCalledWith('devicechange', onDeviceChange);
  });
});
