import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { ACTIVE_ENTITY_FACADE, baseEntityRoutes } from '@processpuzzle/base-entity';
import { BASE_ENTITY_TRANSLOCO_SCOPE, ORG_ADMIN_TRANSLOCO_SCOPE } from './org-admin.i18n';
import { ORGANIZATION_USER_ENTITY_NAME } from './domain/organization-user.descriptors';
import { OrganizationUserContainerComponent } from './feature/organization-user-container.component';
import { OrganizationUserFacade } from './feature/organization-user.facade';
import { ROLE_ASSIGNMENT_TAB } from './feature/role-assignment-tab';
import { OrganizationAdministrationComponent } from './feature/organization-administration.component';
import { ORGANIZATION_ROLE_ENTITY_NAME } from './domain/organization-role.descriptors';
import { OrganizationRoleContainerComponent } from './feature/organization-role-container.component';
import { OrganizationRoleFacade } from './feature/organization-role.facade';

/**
 * A tenant's own administration branch, mounted by `processpuzzle-biz-frontend` under `/{orgKey}/admin`.
 *
 * The path segment is `snakeCaseName('Organization User')` — `organization-user` — because
 * `BaseFormNavigatorSingletonStore` builds the details URL from the entity name; a mismatch stops the
 * username column linking and Edit navigating, with no error anywhere. `entityName` in `data` has to
 * sit on this route and not one deeper, because `readEmbeddedBreadcrumb` takes a level's base URL
 * from the URL accumulated up to the route that declares the name.
 *
 * Roles appear twice: as the organization's catalog, a screen of its own, and as a tab of a user
 * ({@link ROLE_ASSIGNMENT_TAB}), which is where they are assigned.
 */
export const ORG_ADMIN_ROUTES: Routes = [
  // The branch root is the overview of what is administered, not the users: the sidenav's Administration entry
  // lands here, and the cards choose the screen.
  { path: '', pathMatch: 'full', component: OrganizationAdministrationComponent },
  {
    path: 'organization-user',
    title: 'ProcessPuzzle - Users',
    data: { icon: 'group', menuTitle: 'admin.users', entityName: ORGANIZATION_USER_ENTITY_NAME },
    component: OrganizationUserContainerComponent,
    // No `ACTIVE_ENTITY_FACADE` here, unlike the roles: `BaseEntityContainerComponent` renders a bound
    // facade's descriptor in preference to the one it is handed, and the container's is the one carrying
    // the role-assignment tab. With the facade bound, the tab's route existed but no link led to it.
    providers: [adminScopes()],
    children: baseEntityRoutes([], [ROLE_ASSIGNMENT_TAB]),
  },
  {
    path: 'organization-role',
    title: 'ProcessPuzzle - Roles',
    data: { icon: 'badge', menuTitle: 'admin.roles', entityName: ORGANIZATION_ROLE_ENTITY_NAME },
    component: OrganizationRoleContainerComponent,
    providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: OrganizationRoleFacade }, adminScopes()],
    children: baseEntityRoutes([]),
  },
];

/**
 * The transloco scopes this branch needs. Both are required and both aliases are spelled out.
 *
 * Both, because a route declaring `TRANSLOCO_SCOPE` *replaces* the collection it inherits rather than
 * extending it, and the generic tabs and toolbar translate `base_entity.*`. Aliases spelled out,
 * because transloco camel-cases the default one — `org_admin` would silently become `orgAdmin` and
 * miss every key below it.
 */
function adminScopes() {
  return provideTranslocoScope({ scope: BASE_ENTITY_TRANSLOCO_SCOPE, alias: BASE_ENTITY_TRANSLOCO_SCOPE }, { scope: ORG_ADMIN_TRANSLOCO_SCOPE, alias: ORG_ADMIN_TRANSLOCO_SCOPE });
}

/**
 * The facade this library's route resolves `ACTIVE_ENTITY_FACADE` from.
 *
 * Provided at application level rather than on the route, because `useExisting` needs it to already
 * exist — a route-level entry would create a second instance per activation and lose whatever the
 * first had loaded.
 */
export const ORG_ADMIN_FACADE_PROVIDERS = [OrganizationUserFacade, OrganizationRoleFacade];

/** Entity name → facade, for a host's `BASE_ENTITY_FACADE_REGISTRY`. */
export const ORG_ADMIN_ENTITY_FACADES = {
  [ORGANIZATION_USER_ENTITY_NAME]: OrganizationUserFacade,
  [ORGANIZATION_ROLE_ENTITY_NAME]: OrganizationRoleFacade,
};
