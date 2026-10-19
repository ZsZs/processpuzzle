import { afterEach, describe, expect, it, vi } from 'vitest';
import { formatGeoLocation, geoLocationOf } from './geo-location';
import { GeoLocationPipe } from './geo-location.pipe';
import { GeoLocator, GeoLocatorFailure } from './geo-locator';

describe('geoLocationOf', () => {
  it('keeps the coordinates, a non-negative accuracy and the capture time', () => {
    expect(geoLocationOf({ latitude: 1, longitude: 2, accuracy: 3, capturedAt: 'x', other: true })).toEqual({ latitude: 1, longitude: 2, accuracy: 3, capturedAt: 'x' });
    expect(geoLocationOf({ latitude: 1, longitude: 2, accuracy: -1 })).toEqual({ latitude: 1, longitude: 2 });
  });

  it.each([null, 'text', [1, 2], {}, { latitude: 91, longitude: 0 }, { latitude: 0 }])('holds no location in %j', (value) => {
    expect(geoLocationOf(value)).toBeUndefined();
  });
});

describe('formatGeoLocation and ppGeoLocation', () => {
  it('writes the coordinates on one line, with the accuracy when known', () => {
    expect(formatGeoLocation({ latitude: 47.5, longitude: 19.04 })).toBe('47.500000, 19.040000');
    expect(new GeoLocationPipe().transform({ latitude: 47.5, longitude: 19.04, accuracy: 7.6 })).toBe('47.500000, 19.040000 (±8 m)');
    expect(formatGeoLocation(undefined)).toBe('');
  });
});

describe('GeoLocator', () => {
  const locator = new GeoLocator();

  afterEach(() => vi.unstubAllGlobals());

  function stubNavigator(navigator: unknown, isSecureContext = true): void {
    vi.stubGlobal('navigator', navigator);
    vi.stubGlobal('isSecureContext', isSecureContext);
  }

  it('is unavailable without the Geolocation API or outside a secure context', async () => {
    stubNavigator({});
    expect(locator.isAvailable()).toBe(false);
    await expect(locator.locate()).rejects.toEqual(new GeoLocatorFailure('UNAVAILABLE'));

    stubNavigator({ geolocation: {} }, false);
    expect(locator.isAvailable()).toBe(false);
  });

  it('reads a fresh, precise position', async () => {
    const getCurrentPosition = vi.fn((success: PositionCallback, _failure?: PositionErrorCallback, _options?: PositionOptions) =>
      success({ coords: { latitude: 1, longitude: 2, accuracy: 3 }, timestamp: Date.UTC(2026, 9, 17) } as GeolocationPosition),
    );
    stubNavigator({ geolocation: { getCurrentPosition } });

    await expect(locator.locate()).resolves.toEqual({ latitude: 1, longitude: 2, accuracy: 3, capturedAt: '2026-10-17T00:00:00.000Z' });
    expect(getCurrentPosition.mock.calls[0][2]).toMatchObject({ enableHighAccuracy: true, maximumAge: 0 });
  });

  it.each([
    [1, 'PERMISSION_DENIED'],
    [2, 'POSITION_UNAVAILABLE'],
    [3, 'TIMEOUT'],
  ])('names error code %i %s', async (code, reason) => {
    const getCurrentPosition = vi.fn((_: PositionCallback, failure: PositionErrorCallback) => failure({ code } as GeolocationPositionError));
    stubNavigator({ geolocation: { getCurrentPosition } });

    await expect(locator.locate()).rejects.toMatchObject({ reason });
  });

  it('reads the permission and follows its changes', async () => {
    const status = { state: 'denied', onchange: null as null | (() => void) };
    stubNavigator({ permissions: { query: vi.fn(async () => status) } });
    const onChange = vi.fn();

    await expect(locator.isDenied(onChange)).resolves.toBe(true);
    status.state = 'granted';
    status.onchange?.();
    expect(onChange).toHaveBeenCalledWith(false);
  });

  it('assumes no refusal when the browser cannot tell', async () => {
    stubNavigator({});
    await expect(locator.isDenied()).resolves.toBe(false);

    stubNavigator({ permissions: { query: vi.fn(async () => Promise.reject(new TypeError('unsupported'))) } });
    await expect(locator.isDenied()).resolves.toBe(false);
  });
});
