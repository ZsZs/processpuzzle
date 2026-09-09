import { describe, expect, it } from 'vitest';
import { ACTIVE_ENTITY_FACADE } from '@processpuzzle/base-entity';
import { ORG_ADMIN_ENTITY_FACADES, ORG_ADMIN_FACADE_PROVIDERS, ORG_ADMIN_ROUTES } from './org-admin.routes';
import { ORGANIZATION_USER_ENTITY_NAME } from './domain/organization-user.descriptors';
import { OrganizationUserContainerComponent } from './feature/organization-user-container.component';
import { OrganizationUserFacade } from './feature/organization-user.facade';
import { ROLE_ASSIGNMENT_TAB, ROLE_ASSIGNMENT_TAB_SEGMENT } from './feature/role-assignment-tab';
import { RoleAssignmentComponent } from './feature/role-assignment.component';

describe('ORG_ADMIN_ROUTES', () => {
  const userRoute = ORG_ADMIN_ROUTES.find((route) => route.path === 'organization-user');

  it('mounts the users screen and redirects the branch root to it', () => {
    expect(ORG_ADMIN_ROUTES.map((route) => route.path)).toEqual(['', 'organization-user']);
    expect(ORG_ADMIN_ROUTES[0].redirectTo).toBe('organization-user');
    expect(ORG_ADMIN_ROUTES[0].pathMatch).toBe('full');
  });

  // The path is `snakeCaseName('Organization User')`. `BaseFormNavigatorSingletonStore` rebuilds the
  // details URL from the entity name, so any other segment stops the username column linking and Edit
  // navigating, with no error anywhere.
  it('uses the snake-cased entity name as path, as the form navigator expects', () => {
    expect(userRoute?.path).toBe('organization-user');
    expect(userRoute?.data?.['entityName']).toBe(ORGANIZATION_USER_ENTITY_NAME);
  });

  it('advertises itself to the sidenav and mounts the feature container', () => {
    expect(userRoute?.title).toBeTruthy();
    expect(userRoute?.data).toEqual({ icon: 'group', menuTitle: 'admin.users', entityName: 'Organization User' });
    expect(userRoute?.component).toBe(OrganizationUserContainerComponent);
  });

  it('binds the container to this feature facade and registers both transloco scopes', () => {
    // base_entity is not inherited: the generic tabs resolve `base_entity.tabs.*` and a route that
    // declares TRANSLOCO_SCOPE replaces the inherited collection. The aliases are asserted too, because
    // left to transloco's default they would be camel-cased (`orgAdmin`) and no key would resolve.
    const providers = (userRoute?.providers?.flat() ?? []) as Array<{ useValue?: unknown; provide?: unknown; useExisting?: unknown }>;

    expect(providers[0].provide).toBe(ACTIVE_ENTITY_FACADE);
    expect(providers[0].useExisting).toBe(OrganizationUserFacade);
    expect(providers.slice(1).map((provider) => provider.useValue)).toEqual([
      { scope: 'base_entity', alias: 'base_entity' },
      { scope: 'org_admin', alias: 'org_admin' },
    ]);
  });

  it('nests the generic list and details routes, with role assignment beside them', () => {
    // A sibling of details and not a child: assigning roles is another screen *of the user*, addressed
    // by the same `<entity>/<id>` prefix, and deliberately not a section of the profile form.
    expect(userRoute?.children?.map((child) => child.path)).toEqual(['', ':entityId/details', `:entityId/${ROLE_ASSIGNMENT_TAB_SEGMENT}`, 'list']);
  });

  it('answers the role tab link with the component the tab declares', () => {
    const roleRoute = userRoute?.children?.find((child) => child.path === `:entityId/${ROLE_ASSIGNMENT_TAB_SEGMENT}`);

    // Same constant on both sides, so the URL the tab navigates to and the URL that resolves a
    // component cannot drift apart.
    expect(roleRoute?.component).toBe(ROLE_ASSIGNMENT_TAB.component);
    expect(roleRoute?.component).toBe(RoleAssignmentComponent);
  });

  // Roles are the realm's list, read to populate a checkbox set — not something a tenant creates. A
  // route of their own would give them a list and a form that could mint one, and a role minted from a
  // typo is one nothing in the platform ever matches.
  it('gives roles no routable screen of their own', () => {
    expect(ORG_ADMIN_ROUTES.map((route) => route.path)).not.toContain('organization-role');
    expect(userRoute?.children?.some((child) => child.path === 'list')).toBe(true);
  });

  it('offers the facade for application-level provision and registers it under the entity name', () => {
    // Application level rather than route level: the route binds it with `useExisting`, which needs the
    // instance to already exist — a route-level provider would create a second one per activation and
    // lose whatever the first had loaded.
    expect(ORG_ADMIN_FACADE_PROVIDERS).toEqual([OrganizationUserFacade]);
    expect(ORG_ADMIN_ENTITY_FACADES).toEqual({ [ORGANIZATION_USER_ENTITY_NAME]: OrganizationUserFacade });
  });
});
