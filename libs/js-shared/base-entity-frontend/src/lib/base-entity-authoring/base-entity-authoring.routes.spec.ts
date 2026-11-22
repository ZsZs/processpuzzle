import { describe, expect, it } from 'vitest';
import { BaseEntityContainerComponent } from '../base-entity-container.component';
import { ACTIVE_ENTITY_FACADE } from '../base-entity-facade/active-entity-facade.token';
import { snakeCaseName } from '../base-form-navigator/base-form-navigator.store';
import { BaseUrlSegments } from '../base-form-navigator/base-url-segments';
import { EMBEDDED_ENTITY_ROUTE_DATA_KEY, ENTITY_NAME_ROUTE_DATA_KEY } from '../base-form-navigator/entity-route.registry';
import { BASE_ENTITY_AUTHORING_ENTITY_FACADES, BASE_ENTITY_AUTHORING_FACADE_PROVIDERS } from './base-entity-authoring.providers';
import { BASE_ENTITY_AUTHORING_ROUTES } from './base-entity-authoring.routes';
import { EntityDefinitionContainerComponent } from './entity-definition-container.component';
import { ENTITY_ATTRIBUTE_ENTITY_NAME, ENTITY_DEFINITION_ENTITY_NAME, EXCHANGE_RATE_ENTITY_NAME, ORGANIZATION_UNIT_ENTITY_NAME } from './entity-authoring-names';
import { EntityAttributeFacade, EntityDefinitionFacade } from './entity-definition.facade';
import { ExchangeRateEntryFacade } from './exchange-rate-entry.facade';
import { OrganizationUnitFacade } from './organization-unit.facade';

describe('BASE_ENTITY_AUTHORING_ROUTES', () => {
  const definitionRoute = BASE_ENTITY_AUTHORING_ROUTES[0];

  it('mounts a branch for the routable definition, one for the organization’s units and one for its exchange rates', () => {
    expect(BASE_ENTITY_AUTHORING_ROUTES.map((route) => route.path)).toEqual(['entity-definition', 'organization-unit', 'exchange-rate']);
    expect(definitionRoute.component).toBe(EntityDefinitionContainerComponent);
  });

  /**
   * `BaseFormNavigatorSingletonStore` builds a details URL from `snakeCaseName(entityName)`, so a renamed
   * segment silently breaks the list's Name column and Edit navigation.
   */
  it('mounts at the snake-cased entity name', () => {
    expect(definitionRoute.path).toBe(snakeCaseName(ENTITY_DEFINITION_ENTITY_NAME));
    expect(definitionRoute.path).toBe('entity-definition');
  });

  /**
   * `readEmbeddedBreadcrumb` pushes a level when it meets the route that *declares* the name and takes the
   * level's base URL from the URL accumulated so far — one segment too deep doubles it.
   */
  it('declares the entity name on the segment-contributing route', () => {
    expect(definitionRoute.data?.[ENTITY_NAME_ROUTE_DATA_KEY]).toBe(ENTITY_DEFINITION_ENTITY_NAME);
  });

  it('binds the active facade so the container, list and form resolve one descriptor and store', () => {
    expect(definitionRoute.providers).toContainEqual({ provide: ACTIVE_ENTITY_FACADE, useExisting: EntityDefinitionFacade });
  });

  it('mounts the generic list and details screens below it', () => {
    const childPaths = (definitionRoute.children ?? []).map((child) => child.path);

    expect(childPaths).toContain(BaseUrlSegments.ListForm);
    expect(childPaths).toContain(`:${BaseUrlSegments.EntityID}/${BaseUrlSegments.DetailsForm}`);
  });

  it('opens the list when the branch itself is addressed', () => {
    expect(definitionRoute.children?.[0]).toEqual({ path: '', redirectTo: BaseUrlSegments.ListForm, pathMatch: 'full' });
  });

  /** An embedded row has no id of its own, so the URL is what addresses it — hence the nesting. */
  describe('the attribute level', () => {
    const detailsRoute = (definitionRoute.children ?? []).find((child) => child.path === `:${BaseUrlSegments.EntityID}/${BaseUrlSegments.DetailsForm}`);
    const attributeRoute = detailsRoute?.loadChildren?.() as { path: string; data: Record<string, unknown>; providers: unknown[] }[];

    it('hangs below the definition details form', () => {
      expect(attributeRoute).toHaveLength(1);
      expect(attributeRoute[0].path).toBe(snakeCaseName(ENTITY_ATTRIBUTE_ENTITY_NAME));
      expect(attributeRoute[0].path).toBe('entity-attribute');
    });

    it('is marked embedded, so the aggregate guard resolves it against its owner', () => {
      expect(attributeRoute[0].data[ENTITY_NAME_ROUTE_DATA_KEY]).toBe(ENTITY_ATTRIBUTE_ENTITY_NAME);
      expect(attributeRoute[0].data[EMBEDDED_ENTITY_ROUTE_DATA_KEY]).toBe(true);
    });

    it('binds its own facade', () => {
      expect(attributeRoute[0].providers).toContainEqual({ provide: ACTIVE_ENTITY_FACADE, useExisting: EntityAttributeFacade });
    });
  });
});

describe('the units branch', () => {
  const unitRoute = BASE_ENTITY_AUTHORING_ROUTES[1];

  it('mounts at the snake-cased entity name, declared on the segment-contributing route', () => {
    expect(unitRoute.path).toBe(snakeCaseName(ORGANIZATION_UNIT_ENTITY_NAME));
    expect(unitRoute.path).toBe('organization-unit');
    expect(unitRoute.data?.[ENTITY_NAME_ROUTE_DATA_KEY]).toBe(ORGANIZATION_UNIT_ENTITY_NAME);
  });

  it('hosts the generic container and binds its own facade', () => {
    expect(unitRoute.component).toBe(BaseEntityContainerComponent);
    expect(unitRoute.providers).toContainEqual({ provide: ACTIVE_ENTITY_FACADE, useExisting: OrganizationUnitFacade });
  });

  it('mounts the generic list and details screens below it, with no embedded level', () => {
    const childPaths = (unitRoute.children ?? []).map((child) => child.path);

    expect(childPaths).toContain(BaseUrlSegments.ListForm);
    expect(childPaths).toContain(`:${BaseUrlSegments.EntityID}/${BaseUrlSegments.DetailsForm}`);
    const details = (unitRoute.children ?? []).find((child) => child.path === `:${BaseUrlSegments.EntityID}/${BaseUrlSegments.DetailsForm}`);
    expect(details?.loadChildren).toBeUndefined();
  });
});

describe('the exchange rates branch', () => {
  const rateRoute = BASE_ENTITY_AUTHORING_ROUTES[2];

  it('mounts at the snake-cased entity name, declared on the segment-contributing route', () => {
    expect(rateRoute.path).toBe(snakeCaseName(EXCHANGE_RATE_ENTITY_NAME));
    expect(rateRoute.path).toBe('exchange-rate');
    expect(rateRoute.data?.[ENTITY_NAME_ROUTE_DATA_KEY]).toBe(EXCHANGE_RATE_ENTITY_NAME);
  });

  it('hosts the generic container, binds its own facade and has no embedded level', () => {
    expect(rateRoute.component).toBe(BaseEntityContainerComponent);
    expect(rateRoute.providers).toContainEqual({ provide: ACTIVE_ENTITY_FACADE, useExisting: ExchangeRateEntryFacade });
    const details = (rateRoute.children ?? []).find((child) => child.path === `:${BaseUrlSegments.EntityID}/${BaseUrlSegments.DetailsForm}`);
    expect(details).toBeDefined();
    expect(details?.loadChildren).toBeUndefined();
  });
});

describe('the authoring providers', () => {
  it('ships every facade, so a consuming application cannot register half the branch', () => {
    expect(BASE_ENTITY_AUTHORING_FACADE_PROVIDERS).toEqual([EntityDefinitionFacade, EntityAttributeFacade, OrganizationUnitFacade, ExchangeRateEntryFacade]);
  });

  /** An `EMBEDDED_COMPONENTS` control reaches the child's store and descriptor through this map. */
  it('keys every facade by the entity name the descriptors declare', () => {
    expect(BASE_ENTITY_AUTHORING_ENTITY_FACADES).toEqual({
      [ENTITY_DEFINITION_ENTITY_NAME]: EntityDefinitionFacade,
      [ENTITY_ATTRIBUTE_ENTITY_NAME]: EntityAttributeFacade,
      [ORGANIZATION_UNIT_ENTITY_NAME]: OrganizationUnitFacade,
      [EXCHANGE_RATE_ENTITY_NAME]: ExchangeRateEntryFacade,
    });
  });
});
