import { inject, InjectionToken } from '@angular/core';
import { UnitCatalog } from '@processpuzzle/util';
import { OrganizationUnitCatalog } from './organization-unit-catalog';

/**
 * The catalog the quantity, money and dimensions controls and pipes read units and currencies from: the organization's —
 * the standard units and the ones it added — as {@link OrganizationUnitCatalog} last loaded it, which is the standard
 * catalog where nothing loads it. A view, not a snapshot: it follows every reload. An application provides another by
 * providing this token.
 */
export const UNIT_CATALOG = new InjectionToken<UnitCatalog>('UNIT_CATALOG', { providedIn: 'root', factory: () => inject(OrganizationUnitCatalog).live });
