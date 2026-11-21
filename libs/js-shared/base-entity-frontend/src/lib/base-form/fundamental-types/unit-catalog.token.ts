import { InjectionToken } from '@angular/core';
import { STANDARD_UNIT_CATALOG, UnitCatalog } from '@processpuzzle/util';

/**
 * The catalog the quantity, money and dimensions controls and pipes read units and currencies from. The
 * standard catalog unless an application provides another — the place organization-specific units will plug in.
 */
export const UNIT_CATALOG = new InjectionToken<UnitCatalog>('UNIT_CATALOG', { providedIn: 'root', factory: () => STANDARD_UNIT_CATALOG });
