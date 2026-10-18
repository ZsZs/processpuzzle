import { inject, Injectable, Provider } from '@angular/core';
import { provideTranslocoScope } from '@jsverse/transloco';
import { ENTITY_TAB_CONTRIBUTORS, type BaseEntityDescriptor, type EntityTabContributor, type EntityTabDescriptor } from '@processpuzzle/base-entity';
import { BASE_DOCUMENT_TRANSLOCO_SCOPE, ENTITY_REPORTS_I18N_KEY } from '../../base-document.i18n';
import { EntityReportRegistry } from './entity-report.registry';
import { EntityReportsTabComponent } from './entity-reports-tab.component';

/** URL segment of an entity's Reports tab, appended to `<entity>/<id>/`. */
export const ENTITY_REPORTS_TAB_SEGMENT = 'reports';

export const ENTITY_REPORTS_TAB: EntityTabDescriptor = {
  segment: ENTITY_REPORTS_TAB_SEGMENT,
  i18nKey: ENTITY_REPORTS_I18N_KEY,
  component: EntityReportsTabComponent,
};

/** Offers the Reports tab to every entity some report template reports on, and to no other. */
@Injectable({ providedIn: 'root' })
export class EntityReportsTabContributor implements EntityTabContributor {
  private readonly registry = inject(EntityReportRegistry);

  async tabsFor(descriptor: BaseEntityDescriptor): Promise<EntityTabDescriptor[]> {
    return (await this.registry.templatesFor(descriptor.entityName)).length > 0 ? [ENTITY_REPORTS_TAB] : [];
  }
}

/**
 * Registers the Reports tab for the whole application; spread into the root `providers`. The transloco scope
 * is registered too, because the tab renders on the entity's route, which does not register `base_document` —
 * the same reasoning as base-state's `provideEntityStateMachineTab()`.
 */
export function provideEntityReportsTab(): Provider[] {
  return [
    { provide: ENTITY_TAB_CONTRIBUTORS, useExisting: EntityReportsTabContributor, multi: true },
    provideTranslocoScope({ scope: BASE_DOCUMENT_TRANSLOCO_SCOPE, alias: BASE_DOCUMENT_TRANSLOCO_SCOPE }),
  ];
}
