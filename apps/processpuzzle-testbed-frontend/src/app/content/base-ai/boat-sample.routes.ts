import { inject } from '@angular/core';
import { Routes } from '@angular/router';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { entityScreenRoute, EntityScreenResolver } from '@processpuzzle/base-entity';

/**
 * The `Boat` entity of the Base AI samples. Like `Order` under the rule samples, it is metadata — a
 * definition in base-entity-backend's processpuzzle-testbed-entities.yaml — so it has no facade here and its
 * screens are resolved at run-time.
 *
 * What makes it a recognition sample is not anything on this route: it is the seeded `boat` recognition
 * profile. `EntityScreenResolver` asks the registered tab contributors while resolving these screens, and
 * base-ai's contributor answers with the Enrollment tab because that profile exists.
 */
export const BOAT_NAME = 'Boat';
export const BOAT_PATH = 'boat';

export async function boatScreenRoutes(): Promise<Routes> {
  const screens = await inject(EntityScreenResolver).resolve(BOAT_NAME);
  return [{ path: '', ...entityScreenRoute({ entityName: BOAT_NAME, screens, hostPath: BOAT_PATH }) }];
}
