import { AddressLineField, PostalAddress } from './postal-address';

/** The i18n keys, under `base_entity.address.`, a field may be labelled with. */
export type AddressFieldLabel = 'street' | 'address_line2' | 'postal_code' | 'zip_code' | 'postcode' | 'city' | 'town' | 'region' | 'state' | 'province' | 'county' | 'prefecture' | 'canton';

export interface AddressFieldLayout {
  field: AddressLineField;
  label: AddressFieldLabel;
  /** Share of the row's width, as a flex-grow factor — a postal code is narrower than a city. */
  grow: number;
}

/**
 * How one country writes its addresses: the lines in the order they are written, each a row of fields.
 *
 * A field a country does not use — a region in Germany, say — is simply not in its layout, and so not offered.
 * The same layout orders the one-line rendering lists and PDF exports show.
 */
export interface AddressFormat {
  lines: AddressFieldLayout[][];
}

const STREET: AddressFieldLayout = { field: 'street', label: 'street', grow: 1 };
const LINE2: AddressFieldLayout = { field: 'addressLine2', label: 'address_line2', grow: 1 };
const POSTAL_CODE: AddressFieldLayout = { field: 'postalCode', label: 'postal_code', grow: 1 };
const CITY: AddressFieldLayout = { field: 'city', label: 'city', grow: 3 };

function field(base: AddressFieldLayout, label: AddressFieldLabel, grow = base.grow): AddressFieldLayout {
  return { ...base, label, grow };
}

function region(label: AddressFieldLabel, grow = 1): AddressFieldLayout {
  return { field: 'region', label, grow };
}

/** Street first, then postal code and city on one line — most of continental Europe. */
const POSTAL_CODE_CITY: AddressFormat = { lines: [[STREET], [LINE2], [POSTAL_CODE, CITY]] };

/** For a country without a format of its own: every field, international order. */
export const DEFAULT_ADDRESS_FORMAT: AddressFormat = { lines: [[STREET], [LINE2], [CITY, region('region', 2)], [POSTAL_CODE]] };

const ADDRESS_FORMATS: Readonly<Record<string, AddressFormat>> = {
  AT: POSTAL_CODE_CITY,
  BE: POSTAL_CODE_CITY,
  CH: { lines: [[STREET], [LINE2], [POSTAL_CODE, CITY], [region('canton')]] },
  CZ: POSTAL_CODE_CITY,
  DE: POSTAL_CODE_CITY,
  DK: POSTAL_CODE_CITY,
  FI: POSTAL_CODE_CITY,
  FR: POSTAL_CODE_CITY,
  NL: POSTAL_CODE_CITY,
  NO: POSTAL_CODE_CITY,
  PL: POSTAL_CODE_CITY,
  PT: POSTAL_CODE_CITY,
  SE: POSTAL_CODE_CITY,
  SK: POSTAL_CODE_CITY,
  SI: POSTAL_CODE_CITY,
  HR: POSTAL_CODE_CITY,
  ES: { lines: [[STREET], [LINE2], [POSTAL_CODE, CITY], [region('province')]] },
  IT: { lines: [[STREET], [LINE2], [POSTAL_CODE, CITY, region('province', 1)]] },
  // Hungarian addresses are written city first: Budapest, Fő utca 1., 1011.
  HU: { lines: [[field(CITY, 'city', 3), POSTAL_CODE], [STREET], [LINE2]] },
  GB: { lines: [[STREET], [LINE2], [field(CITY, 'town')], [region('county')], [field(POSTAL_CODE, 'postcode')]] },
  IE: { lines: [[STREET], [LINE2], [field(CITY, 'town')], [region('county')], [field(POSTAL_CODE, 'postcode')]] },
  US: { lines: [[STREET], [LINE2], [field(CITY, 'city', 3), region('state', 2), field(POSTAL_CODE, 'zip_code', 1)]] },
  CA: { lines: [[STREET], [LINE2], [field(CITY, 'city', 3), region('province', 2), field(POSTAL_CODE, 'postal_code', 1)]] },
  AU: { lines: [[STREET], [LINE2], [field(CITY, 'town', 3), region('state', 1), field(POSTAL_CODE, 'postcode', 1)]] },
  BR: { lines: [[STREET], [LINE2], [CITY, region('state', 1)], [POSTAL_CODE]] },
  MX: { lines: [[STREET], [LINE2], [POSTAL_CODE, CITY, region('state', 2)]] },
  // Japanese addresses run from the largest unit to the smallest.
  JP: { lines: [[POSTAL_CODE, region('prefecture', 2)], [CITY], [STREET], [LINE2]] },
  CN: { lines: [[region('province', 2), CITY], [STREET], [LINE2], [POSTAL_CODE]] },
};

/** The format of a country, by its ISO 3166-1 alpha-2 code; the international default for any other. */
export function addressFormatOf(country: string | undefined): AddressFormat {
  return (country && ADDRESS_FORMATS[country.toUpperCase()]) || DEFAULT_ADDRESS_FORMAT;
}

/**
 * The address on one line, in the order its country writes it, with the country's name last — for list
 * cells, PDF exports and a geocoder's free-text query. Empty when the address holds nothing.
 */
export function formatAddress(address: PostalAddress, countryName?: (code: string) => string): string {
  const lines = addressFormatOf(address.country).lines.map((line) =>
    line
      .map(({ field }) => address[field]?.trim())
      .filter(Boolean)
      .join(' '),
  );
  const country = address.country ? (countryName?.(address.country) ?? address.country) : undefined;
  const parts = lines.filter(Boolean);
  if (parts.length === 0) return '';
  return [...parts, country].filter(Boolean).join(', ');
}
