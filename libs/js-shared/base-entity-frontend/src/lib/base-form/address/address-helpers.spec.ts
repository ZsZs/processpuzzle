import { afterEach, describe, expect, it, vi } from 'vitest';
import { addressFormatOf, DEFAULT_ADDRESS_FORMAT, formatAddress } from './address-formats';
import { AddressGeocoder } from './address-geocoder';
import { googleMapsUrl, mapEmbedUrl } from './address-map';
import { COUNTRY_CODES, countryName, countryOfLanguageTag, countryOptions } from './countries';
import { addressOf, geoPointOf, isAddressEmpty } from './postal-address';

describe('postal-address', () => {
  it('reads anything that is not an object as an empty address', () => {
    expect(addressOf('Main St')).toEqual({});
    expect(addressOf([1])).toEqual({});
    expect(addressOf(null)).toEqual({});
    expect(addressOf({ city: 'Bern' })).toEqual({ city: 'Bern' });
  });

  it('counts a country or coordinates alone as no address', () => {
    expect(isAddressEmpty({ country: 'HU', latitude: 1, longitude: 2 })).toBe(true);
    expect(isAddressEmpty({ city: ' ' })).toBe(true);
    expect(isAddressEmpty({ city: 'Pécs' })).toBe(false);
  });

  it('gives a point only for two coordinates in range', () => {
    expect(geoPointOf({ latitude: 47.5, longitude: 19 })).toEqual({ latitude: 47.5, longitude: 19 });
    expect(geoPointOf({ latitude: 47.5 })).toBeUndefined();
    expect(geoPointOf({ latitude: 91, longitude: 19 })).toBeUndefined();
    expect(geoPointOf({ latitude: Number.NaN, longitude: 19 })).toBeUndefined();
  });
});

describe('address-formats', () => {
  it('falls back to the international format for an unknown or missing country', () => {
    expect(addressFormatOf('XX')).toBe(DEFAULT_ADDRESS_FORMAT);
    expect(addressFormatOf(undefined)).toBe(DEFAULT_ADDRESS_FORMAT);
    expect(addressFormatOf('de')).toBe(addressFormatOf('DE'));
  });

  it('writes the address on one line in its country order, the country last', () => {
    expect(formatAddress({ country: 'HU', city: 'Budapest', postalCode: '1011', street: 'Fő utca 1.' }, () => 'Hungary')).toBe('Budapest 1011, Fő utca 1., Hungary');
    expect(formatAddress({ country: 'DE', street: 'Hauptstr. 1', postalCode: '10115', city: 'Berlin' })).toBe('Hauptstr. 1, 10115 Berlin, DE');
  });

  it('writes nothing for an address with only a country', () => {
    expect(formatAddress({ country: 'DE' })).toBe('');
  });
});

describe('countries', () => {
  it('lists every code once', () => {
    expect(new Set(COUNTRY_CODES).size).toBe(COUNTRY_CODES.length);
    expect(COUNTRY_CODES.length).toBe(249);
  });

  it('names and sorts the countries in the given language', () => {
    expect(countryName('DE', 'de')).toBe('Deutschland');
    expect(countryName('DE', 'not a language tag!')).toBe('DE');
    const names = countryOptions('en').map((option) => option.name);
    expect(names).toEqual([...names].sort((left, right) => left.localeCompare(right, 'en')));
  });

  it('reads the country from a language tag', () => {
    expect(countryOfLanguageTag('hu-HU')).toBe('HU');
    expect(countryOfLanguageTag('en_gb')).toBe('GB');
    expect(countryOfLanguageTag('en')).toBeUndefined();
    expect(countryOfLanguageTag(undefined)).toBeUndefined();
  });
});

describe('address-map', () => {
  it('frames the point in an OpenStreetMap embed with a marker', () => {
    const url = mapEmbedUrl('OPENSTREETMAP', { latitude: 47.5, longitude: 19 });
    expect(url).toContain('bbox=18.996000,47.496000,19.004000,47.504000');
    expect(url).toContain('marker=47.5,19');
  });

  it('opens the point in Google Maps', () => {
    expect(googleMapsUrl({ latitude: 47.5, longitude: 19 })).toBe('https://www.google.com/maps/search/?api=1&query=47.5,19');
  });
});

describe('AddressGeocoder', () => {
  afterEach(() => vi.unstubAllGlobals());

  function stubFetch(response: Partial<Response>) {
    const fetchMock = vi.fn(async (_url: string) => response as Response);
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
  }

  it('asks Nominatim with a structured query and reads the first hit', async () => {
    const fetchMock = stubFetch({ ok: true, json: async () => [{ lat: '47.5', lon: '19.04' }] });

    const point = await new AddressGeocoder().geocode({ country: 'HU', street: 'Fő utca 1.', city: 'Budapest', postalCode: '1011', region: 'Pest' }, 'hu');

    expect(point).toEqual({ latitude: 47.5, longitude: 19.04 });
    const url = new URL(String(fetchMock.mock.calls[0][0]));
    expect(url.origin + url.pathname).toBe('https://nominatim.openstreetmap.org/search');
    expect(Object.fromEntries(url.searchParams)).toEqual({
      format: 'jsonv2',
      limit: '1',
      street: 'Fő utca 1.',
      city: 'Budapest',
      postalcode: '1011',
      state: 'Pest',
      countrycodes: 'hu',
      'accept-language': 'hu',
    });
  });

  it('answers nothing when there is no hit, the service fails or cannot be reached', async () => {
    stubFetch({ ok: true, json: async () => [] });
    expect(await new AddressGeocoder().geocode({ city: 'Nowhere' })).toBeUndefined();

    stubFetch({ ok: false });
    expect(await new AddressGeocoder().geocode({ city: 'Nowhere' })).toBeUndefined();

    vi.stubGlobal(
      'fetch',
      vi.fn(async () => Promise.reject(new Error('offline'))),
    );
    expect(await new AddressGeocoder().geocode({ city: 'Nowhere' })).toBeUndefined();
  });
});
