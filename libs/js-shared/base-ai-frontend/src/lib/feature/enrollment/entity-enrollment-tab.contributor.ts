import { inject, Injectable, Provider } from '@angular/core';
import { provideTranslocoScope } from '@jsverse/transloco';
import { ENTITY_TAB_CONTRIBUTORS, type BaseEntityDescriptor, type EntityTabContributor, type EntityTabDescriptor } from '@processpuzzle/base-entity';
import { BASE_AI_TRANSLOCO_SCOPE } from '../../base-ai.i18n';
import { ProfiledEntityRegistry } from '../../domain/profile/profiled-entity.registry';
import { ENTITY_ENROLLMENT_TAB } from './entity-enrollment-tab';

/**
 * Offers the Enrollment tab to every entity type that has a recognition profile, and to no other.
 *
 * base-ai reaching into another feature's screens, which is why it is a contributor: `Boat` is a
 * base-entity definition, and neither base-entity nor the application mounting its screens knows that
 * base-ai has a view to add. The binding is the entity's definition code on both sides.
 */
@Injectable({ providedIn: 'root' })
export class EntityEnrollmentTabContributor implements EntityTabContributor {
  private readonly profiles = inject(ProfiledEntityRegistry);

  async tabsFor(descriptor: BaseEntityDescriptor): Promise<EntityTabDescriptor[]> {
    return (await this.profiles.profileFor(descriptor.entityName)) ? [ENTITY_ENROLLMENT_TAB] : [];
  }
}

/**
 * Registers the Enrollment tab for the whole application; spread into the root `providers`. The transloco
 * scope is registered too, because the tab's label renders on the subject's route, which does not register
 * `base_ai` itself — the same reasoning as base-state's `provideEntityStateMachineTab()`.
 */
export function provideEntityEnrollmentTab(): Provider[] {
  return [
    { provide: ENTITY_TAB_CONTRIBUTORS, useExisting: EntityEnrollmentTabContributor, multi: true },
    provideTranslocoScope({ scope: BASE_AI_TRANSLOCO_SCOPE, alias: BASE_AI_TRANSLOCO_SCOPE }),
  ];
}
