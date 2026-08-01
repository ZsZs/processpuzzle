import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { BASE_ENTITY_ROUTES } from '@processpuzzle/base-entity';
import { BASE_APP_TRANSLOCO_SCOPE, BASE_ENTITY_TRANSLOCO_SCOPE } from './base-app.i18n';
import { APP_DEFINITION_ENTITY_NAME } from './domain/app-definition.descriptors';
import { AppDefinitionContainerComponent } from './feature/app-definition-container.component';

/**
 * The path segment has to be `snakeCaseName('App Definition')`, because
 * `BaseFormNavigatorSingletonStore` builds the details URL from the entity name — see the same
 * constraint on `BASE_RULE_ROUTES`.
 */
export const BASE_APP_ROUTES: Routes = [
  {
    path: 'app-definition',
    title: 'ProcessPuzzle Design - Applications',
    data: { icon: 'web', menuTitle: 'design.applications', entityName: APP_DEFINITION_ENTITY_NAME },
    component: AppDefinitionContainerComponent,
    // Provided here rather than on the container, so the base-entity tabs, list and form rendered in the
    // child routes resolve the entity and attribute labels from the same scopes. Both are needed: a route
    // that declares TRANSLOCO_SCOPE replaces the collection it inherits rather than adding to it, and the
    // generic tabs translate the framework's own `base_entity.*` keys (see BASE_ENTITY_TRANSLOCO_SCOPE).
    providers: [provideTranslocoScope({ scope: BASE_ENTITY_TRANSLOCO_SCOPE, alias: BASE_ENTITY_TRANSLOCO_SCOPE }, { scope: BASE_APP_TRANSLOCO_SCOPE, alias: BASE_APP_TRANSLOCO_SCOPE })],
    children: BASE_ENTITY_ROUTES,
  },
];
