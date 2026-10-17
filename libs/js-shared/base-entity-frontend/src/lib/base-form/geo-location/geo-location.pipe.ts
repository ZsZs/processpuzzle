import { Pipe, PipeTransform } from '@angular/core';
import { formatGeoLocation } from './geo-location';

/** A `GEO_LOCATION` value on one line. Usage: `{{ row[attr.attrName] | ppGeoLocation }}`. */
@Pipe({ name: 'ppGeoLocation', standalone: true })
export class GeoLocationPipe implements PipeTransform {
  transform(value: unknown): string {
    return formatGeoLocation(value);
  }
}
