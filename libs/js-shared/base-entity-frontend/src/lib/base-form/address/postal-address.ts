/** Where the coordinates of an address came from — derived from the address, or typed in by the user. */
export type LocationSource = 'GEOCODED' | 'MANUAL';

/**
 * The value of an `ADDRESS` attribute: one object, held by a single form control and saved as is.
 *
 * `country` is an ISO 3166-1 alpha-2 code (`HU`, `DE`, `US`), which is also what decides the layout the
 * fields are edited in — see `address-formats.ts`. The coordinates are only filled when the attribute shows a
 * map; `locationSource` remembers whether the user placed them by hand, so a later change of the address does
 * not move a pin the user put somewhere on purpose.
 */
export interface PostalAddress {
  country?: string;
  street?: string;
  addressLine2?: string;
  postalCode?: string;
  city?: string;
  /** State, province, county or prefecture — whatever the country calls its first-level subdivision. */
  region?: string;
  latitude?: number;
  longitude?: number;
  locationSource?: LocationSource;
}

/** The fields of a {@link PostalAddress} a user types — everything but the country and the location. */
export type AddressLineField = 'street' | 'addressLine2' | 'postalCode' | 'city' | 'region';

export const ADDRESS_LINE_FIELDS: readonly AddressLineField[] = ['street', 'addressLine2', 'postalCode', 'city', 'region'];

export interface GeoPoint {
  latitude: number;
  longitude: number;
}

/** The value of an `ADDRESS` attribute as an address — anything that is not an object holds none. */
export function addressOf(value: unknown): PostalAddress {
  return value !== null && typeof value === 'object' && !Array.isArray(value) ? (value as PostalAddress) : {};
}

/** True when no line of the address holds text — a country alone, or coordinates alone, are no address. */
export function isAddressEmpty(address: PostalAddress): boolean {
  return ADDRESS_LINE_FIELDS.every((field) => !address[field]?.trim());
}

/** The coordinates of the address, when it has both. */
export function geoPointOf(address: PostalAddress): GeoPoint | undefined {
  const { latitude, longitude } = address;
  return isCoordinate(latitude, 90) && isCoordinate(longitude, 180) ? { latitude, longitude } : undefined;
}

function isCoordinate(value: unknown, bound: number): value is number {
  return typeof value === 'number' && Number.isFinite(value) && Math.abs(value) <= bound;
}
