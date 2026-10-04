import type { EntityTabDescriptor } from '@processpuzzle/base-entity';
import { ENTITY_ENROLLMENT_I18N_KEY } from '../../base-ai.i18n';
import { EntityEnrollmentTabComponent } from './entity-enrollment-tab.component';

/** URL segment of the subject's Recognition tab — its enrollment — appended to `<entity>/<id>/`. */
export const ENTITY_ENROLLMENT_TAB_SEGMENT = 'recognition';

/**
 * The Enrollment tab as `EntityScreenResolver` hands it to `baseEntityRoutes`. One object shared by every
 * profiled entity: the component works out which subject it shows from the outlet data and the route.
 */
export const ENTITY_ENROLLMENT_TAB: EntityTabDescriptor = {
  segment: ENTITY_ENROLLMENT_TAB_SEGMENT,
  i18nKey: ENTITY_ENROLLMENT_I18N_KEY,
  component: EntityEnrollmentTabComponent,
};
