import { Injectable } from '@angular/core';
import { GeoPoint, PostalAddress } from './postal-address';

/**
 * Finds the coordinates of an address — how an `ADDRESS` attribute with a map places its pin.
 *
 * The default asks OpenStreetMap's public Nominatim service. A host that needs another geocoder — its own
 * Nominatim, Google's Geocoding API — provides a replacement:
 *
 * ```ts
 * providers: [{ provide: AddressGeocoder, useClass: MyGeocoder }]
 * ```
 *
 * Nominatim's usage policy allows about one request a second and no autocomplete, which is why the control
 * geocodes when an address field is left rather than on every keystroke.
 */
@Injectable({ providedIn: 'root' })
export class AddressGeocoder {
  protected readonly endpoint: string = 'https://nominatim.openstreetmap.org/search';

  /** The address's coordinates, or `undefined` when it cannot be found or the service does not answer. */
  async geocode(address: PostalAddress, language?: string): Promise<GeoPoint | undefined> {
    try {
      // `fetch` rather than `HttpClient`: the host's interceptors attach tokens and report errors for *its* API,
      // and neither belongs on a request to a third party.
      const response = await fetch(`${this.endpoint}?${this.query(address, language)}`, { headers: { Accept: 'application/json' } });
      if (!response.ok) return undefined;
      const [hit] = (await response.json()) as Array<{ lat?: string; lon?: string }>;
      const latitude = Number(hit?.lat);
      const longitude = Number(hit?.lon);
      return hit && Number.isFinite(latitude) && Number.isFinite(longitude) ? { latitude, longitude } : undefined;
    } catch {
      return undefined;
    }
  }

  /** Nominatim's structured query: one parameter per address part rather than a free-text string. */
  protected query(address: PostalAddress, language?: string): string {
    const params = new URLSearchParams({ format: 'jsonv2', limit: '1' });
    const street = address.street?.trim();
    if (street) params.set('street', street);
    if (address.city?.trim()) params.set('city', address.city.trim());
    if (address.postalCode?.trim()) params.set('postalcode', address.postalCode.trim());
    if (address.region?.trim()) params.set('state', address.region.trim());
    if (address.country) params.set('countrycodes', address.country.toLowerCase());
    if (language) params.set('accept-language', language);
    return params.toString();
  }
}
