import { Injectable } from '@angular/core';
import { GeoLocation } from './geo-location';

/** Why a reading failed — the codes of `GeolocationPositionError`, plus `UNAVAILABLE` for a browser without the API. */
export type GeoLocatorError = 'PERMISSION_DENIED' | 'POSITION_UNAVAILABLE' | 'TIMEOUT' | 'UNAVAILABLE';

export class GeoLocatorFailure extends Error {
  constructor(readonly reason: GeoLocatorError) {
    super(`The current location could not be read: ${reason}`);
  }
}

/**
 * Reads the device's current location — how a `GEO_LOCATION` attribute fills itself.
 *
 * The default asks the browser's Geolocation API, which only exists in a secure context (HTTPS or localhost).
 * A host that knows the location otherwise — a native shell, a fixed kiosk position — provides a replacement:
 *
 * ```ts
 * providers: [{ provide: GeoLocator, useClass: MyGeoLocator }]
 * ```
 */
@Injectable({ providedIn: 'root' })
export class GeoLocator {
  /** Whether a location can be asked for at all: the API exists here. The user may still refuse it. */
  isAvailable(): boolean {
    return globalThis.isSecureContext !== false && !!globalThis.navigator?.geolocation;
  }

  /**
   * Whether the user has already refused location access for this site. Resolves `false` when the browser
   * cannot tell — the request itself then finds out. `onChange` is called when the user changes their mind.
   */
  async isDenied(onChange?: (denied: boolean) => void): Promise<boolean> {
    try {
      const status = await globalThis.navigator?.permissions?.query({ name: 'geolocation' });
      if (!status) return false;
      if (onChange) status.onchange = () => onChange(status.state === 'denied');
      return status.state === 'denied';
    } catch {
      return false;
    }
  }

  /** The current location, or a {@link GeoLocatorFailure} saying why there is none. */
  locate(): Promise<GeoLocation> {
    return new Promise((resolve, reject) => {
      if (!this.isAvailable()) {
        reject(new GeoLocatorFailure('UNAVAILABLE'));
        return;
      }
      globalThis.navigator.geolocation.getCurrentPosition(
        (position) =>
          resolve({
            latitude: position.coords.latitude,
            longitude: position.coords.longitude,
            accuracy: position.coords.accuracy,
            capturedAt: new Date(position.timestamp).toISOString(),
          }),
        (error) => reject(new GeoLocatorFailure(reasonOf(error.code))),
        // A fresh, precise reading: the user pressed the button to record where they are *now*.
        { enableHighAccuracy: true, maximumAge: 0, timeout: 20000 },
      );
    });
  }
}

function reasonOf(code: number): GeoLocatorError {
  if (code === 1) return 'PERMISSION_DENIED';
  if (code === 3) return 'TIMEOUT';
  return 'POSITION_UNAVAILABLE';
}
