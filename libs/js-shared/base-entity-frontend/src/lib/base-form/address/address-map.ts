import { GeoPoint } from './postal-address';

/**
 * Who draws the map of an `ADDRESS` attribute. OpenStreetMap is the default: its embed needs no API key.
 * Whichever draws it, a click on the map opens the location in Google Maps.
 */
export type MapProvider = 'OPENSTREETMAP' | 'GOOGLE';

export const MAP_PROVIDERS: readonly MapProvider[] = ['OPENSTREETMAP', 'GOOGLE'];

export const DEFAULT_MAP_PROVIDER: MapProvider = 'OPENSTREETMAP';

/** Half the side of the box the OpenStreetMap embed frames, in degrees — roughly a few streets around the pin. */
const OSM_SPAN = 0.004;

/** The URL of an embeddable map centred on `point`, with a marker on it. */
export function mapEmbedUrl(provider: MapProvider, point: GeoPoint): string {
  const { latitude, longitude } = point;
  if (provider === 'GOOGLE') {
    return `https://maps.google.com/maps?q=${latitude},${longitude}&z=16&output=embed`;
  }
  const bbox = [longitude - OSM_SPAN, latitude - OSM_SPAN, longitude + OSM_SPAN, latitude + OSM_SPAN].map((value) => value.toFixed(6)).join(',');
  return `https://www.openstreetmap.org/export/embed.html?bbox=${bbox}&layer=mapnik&marker=${latitude},${longitude}`;
}

/** The location in Google Maps, which is what a click on the map opens. */
export function googleMapsUrl(point: GeoPoint): string {
  return `https://www.google.com/maps/search/?api=1&query=${point.latitude},${point.longitude}`;
}
