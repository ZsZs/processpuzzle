import { geoPointOf, GeoPoint } from '../address/postal-address';

/**
 * The value of a `GEO_LOCATION` attribute: where the device was when the user pressed the control's button.
 *
 * `accuracy` is the radius of the device's uncertainty in metres, `capturedAt` the ISO 8601 instant of the
 * reading. Both come from the browser's Geolocation API; neither is typed by a user.
 */
export interface GeoLocation extends GeoPoint {
  accuracy?: number;
  capturedAt?: string;
}

/** The value of a `GEO_LOCATION` attribute as a location — anything without two valid coordinates holds none. */
export function geoLocationOf(value: unknown): GeoLocation | undefined {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) return undefined;
  const candidate = value as Partial<GeoLocation>;
  const point = geoPointOf(candidate);
  if (!point) return undefined;
  const location: GeoLocation = { ...point };
  if (typeof candidate.accuracy === 'number' && Number.isFinite(candidate.accuracy) && candidate.accuracy >= 0) location.accuracy = candidate.accuracy;
  if (typeof candidate.capturedAt === 'string') location.capturedAt = candidate.capturedAt;
  return location;
}

/** The coordinates on one line — `47.497900, 19.040200 (±12 m)` — or an empty string when the value holds none. */
export function formatGeoLocation(value: unknown): string {
  const location = geoLocationOf(value);
  if (!location) return '';
  const coordinates = `${location.latitude.toFixed(6)}, ${location.longitude.toFixed(6)}`;
  return location.accuracy === undefined ? coordinates : `${coordinates} (±${Math.round(location.accuracy)} m)`;
}
