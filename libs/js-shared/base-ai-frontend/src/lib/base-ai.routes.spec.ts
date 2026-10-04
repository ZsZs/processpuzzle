import { provideTranslocoScope } from '@jsverse/transloco';
import { ACTIVE_ENTITY_FACADE, BaseEntityContainerComponent } from '@processpuzzle/base-entity';
import { describe, expect, it } from 'vitest';
import { BASE_AI_ROUTES } from './base-ai.routes';
import { RECOGNITION_PROFILE_ENTITY_NAME } from './domain/profile/recognition-profile';
import { RecognitionProfileFacade } from './feature/profile/recognition-profile.facade';

describe('BASE_AI_ROUTES', () => {
  const [route] = BASE_AI_ROUTES;

  it('mounts only profile authoring with navigation and entity metadata', () => {
    expect(BASE_AI_ROUTES.map((entry) => entry.path)).toEqual(['recognition-profile']);
    expect(route.component).toBe(BaseEntityContainerComponent);
    expect(route.title).toBe('ProcessPuzzle - Recognition Profiles');
    expect(route.data).toEqual({ icon: 'center_focus_strong', menuTitle: 'ai.profiles', entityName: RECOGNITION_PROFILE_ENTITY_NAME });
  });

  it('binds the active facade and both translation scopes', () => {
    expect(route.providers).toEqual([
      { provide: ACTIVE_ENTITY_FACADE, useExisting: RecognitionProfileFacade },
      provideTranslocoScope({ scope: 'base_entity', alias: 'base_entity' }, { scope: 'base_ai', alias: 'base_ai' }),
    ]);
  });

  it('provides generic CRUD screens without enrolling profiles themselves', () => {
    expect(route.children?.map((child) => child.path)).toEqual(['', ':entityId/details', 'list']);
  });
});
