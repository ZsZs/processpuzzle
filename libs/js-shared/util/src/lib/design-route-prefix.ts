import { InjectionToken } from '@angular/core';

export const DESIGN_ROUTE_PREFIX = new InjectionToken<string>('DESIGN_ROUTE_PREFIX', {
  providedIn: 'root',
  factory: () => '',
});
