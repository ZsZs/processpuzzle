import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { ACTIVE_ENTITY_FACADE, BaseEntityContainerComponent, baseEntityRoutes } from '@processpuzzle/base-entity';
import { BASE_AI_TRANSLOCO_SCOPE, BASE_ENTITY_TRANSLOCO_SCOPE } from './base-ai.i18n';
import { RECOGNITION_PROFILE_ENTITY_NAME } from './domain/profile/recognition-profile';
import { RecognitionProfileFacade } from './feature/profile/recognition-profile.facade';

/**
 * The authoring screens of the feature: the list and form of a `Recognition Profile`.
 *
 * The path segment has to be `snakeCaseName('Recognition Profile')`, because the form navigator builds the
 * details URL from the entity name — the constraint every base-* route branch carries — and `entityName` in
 * `data` has to sit on this route, the one contributing the segment.
 *
 * Enrollment is deliberately not here: a gallery belongs to a subject, so it is a tab on the *subject's*
 * screens, contributed by `provideEntityEnrollmentTab()`.
 */
export const BASE_AI_ROUTES: Routes = [
  {
    path: 'recognition-profile',
    title: 'ProcessPuzzle - Recognition Profiles',
    data: { icon: 'center_focus_strong', menuTitle: 'ai.profiles', entityName: RECOGNITION_PROFILE_ENTITY_NAME },
    component: BaseEntityContainerComponent,
    providers: [
      { provide: ACTIVE_ENTITY_FACADE, useExisting: RecognitionProfileFacade },
      provideTranslocoScope({ scope: BASE_ENTITY_TRANSLOCO_SCOPE, alias: BASE_ENTITY_TRANSLOCO_SCOPE }, { scope: BASE_AI_TRANSLOCO_SCOPE, alias: BASE_AI_TRANSLOCO_SCOPE }),
    ],
    children: baseEntityRoutes(),
  },
];
