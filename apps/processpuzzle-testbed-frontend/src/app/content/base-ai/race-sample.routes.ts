import { inject } from '@angular/core';
import { Routes } from '@angular/router';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { entityScreenRoute, EntityScreenResolver } from '@processpuzzle/base-entity';
import { BOAT_NAME } from './boat-sample.routes';

/**
 * The race organisation around the recognition sample — `Race`, `Registration` and `Race Observation`, all
 * metadata in base-entity-backend's processpuzzle-testbed-entities.yaml. This is the *application's* side:
 * base-ai knows none of it. A race's registrations are the constraint the Recognize page hands the camera; a
 * recognized or ticked boat becomes an observation.
 *
 * Each path is `snakeCaseName` of its entity name, as the Name column and Edit navigation require.
 */
export const RACE_NAME = 'Race';
export const RACE_PATH = 'race';
export const REGISTRATION_NAME = 'Registration';
export const REGISTRATION_PATH = 'registration';
export const OBSERVATION_NAME = 'Race Observation';
export const OBSERVATION_PATH = 'race-observation';

/** Definition codes, for the REST calls of the Recognize page. */
export const RACE_CODE = 'race';
export const REGISTRATION_CODE = 'race-registration';
export const OBSERVATION_CODE = 'race-observation';

/**
 * A screen's FOREIGN_KEY controls find the linked type's store through the resolved-entity cache, which only
 * holds types resolved so far. The linked types are resolved first, so that a Registration opened straight
 * from a bookmark still shows its race and boat.
 */
function screenRoutes(entityName: string, hostPath: string, linked: string[]): () => Promise<Routes> {
  return async () => {
    const resolver = inject(EntityScreenResolver);
    for (const name of linked) await resolver.resolve(name);
    const screens = await resolver.resolve(entityName);
    return [{ path: '', ...entityScreenRoute({ entityName, screens, hostPath }) }];
  };
}

export const raceScreenRoutes = screenRoutes(RACE_NAME, RACE_PATH, []);
export const registrationScreenRoutes = screenRoutes(REGISTRATION_NAME, REGISTRATION_PATH, [RACE_NAME, BOAT_NAME]);
export const observationScreenRoutes = screenRoutes(OBSERVATION_NAME, OBSERVATION_PATH, [RACE_NAME, BOAT_NAME]);
