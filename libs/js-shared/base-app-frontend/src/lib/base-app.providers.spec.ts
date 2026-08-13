import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { BASE_ENTITY_FACADE_REGISTRY, BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { BASE_APP_ENTITY_FACADES, BASE_APP_FACADE_PROVIDERS } from './base-app.providers';
import { AppDefinition, ModuleMount, NavItem, RegionDefinition, RouteDefinition, WidgetInstance } from './domain/app-definition';
import {
  APP_DEFINITION_ENTITY_NAME,
  APP_MODULE_MOUNT_ENTITY_NAME,
  APP_NAV_ITEM_ENTITY_NAME,
  APP_REGION_ENTITY_NAME,
  APP_ROUTE_ENTITY_NAME,
  APP_WIDGET_ENTITY_NAME,
  MODULE_DEFINITION_ENTITY_NAME,
} from './domain/app-entity-names';
import { ModuleDefinition } from './domain/module-definition';

describe('BASE_APP facade providers', () => {
  let injector: Injector;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { APP_SERVICE_ROOT: 'http://localhost:3000/organizations/processpuzzle-testbed' } } },
        ...BASE_APP_FACADE_PROVIDERS,
        // The embedded facades reach each other's stores and descriptors through the registry, which is
        // exactly what the application wires up by spreading the same map.
        { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: BASE_APP_ENTITY_FACADES },
      ],
    });
    injector = TestBed.inject(Injector);
  });

  const descriptorOf = (entityName: string): BaseEntityDescriptor => injector.get(BASE_APP_ENTITY_FACADES[entityName]).descriptor;

  it('registers the two routable definitions and the five embedded levels below them', () => {
    expect(Object.keys(BASE_APP_ENTITY_FACADES)).toEqual([
      APP_DEFINITION_ENTITY_NAME,
      MODULE_DEFINITION_ENTITY_NAME,
      APP_REGION_ENTITY_NAME,
      APP_ROUTE_ENTITY_NAME,
      APP_MODULE_MOUNT_ENTITY_NAME,
      APP_NAV_ITEM_ENTITY_NAME,
      APP_WIDGET_ENTITY_NAME,
    ]);
    expect(BASE_APP_FACADE_PROVIDERS).toHaveLength(Object.keys(BASE_APP_ENTITY_FACADES).length);
  });

  it('keys every facade by the entity name its own descriptor declares', () => {
    Object.entries(BASE_APP_ENTITY_FACADES).forEach(([entityName, facadeToken]) => {
      expect(injector.get(facadeToken).entityName).toBe(entityName);
    });
  });

  it('gives each nested definition the entity type its blank row is minted from', () => {
    expect(injector.get(BASE_APP_ENTITY_FACADES[APP_DEFINITION_ENTITY_NAME]).entityType).toBe(AppDefinition);
    expect(injector.get(BASE_APP_ENTITY_FACADES[MODULE_DEFINITION_ENTITY_NAME]).entityType).toBe(ModuleDefinition);
    expect(injector.get(BASE_APP_ENTITY_FACADES[APP_REGION_ENTITY_NAME]).entityType).toBe(RegionDefinition);
    expect(injector.get(BASE_APP_ENTITY_FACADES[APP_ROUTE_ENTITY_NAME]).entityType).toBe(RouteDefinition);
    expect(injector.get(BASE_APP_ENTITY_FACADES[APP_MODULE_MOUNT_ENTITY_NAME]).entityType).toBe(ModuleMount);
    expect(injector.get(BASE_APP_ENTITY_FACADES[APP_NAV_ITEM_ENTITY_NAME]).entityType).toBe(NavItem);
    expect(injector.get(BASE_APP_ENTITY_FACADES[APP_WIDGET_ENTITY_NAME]).entityType).toBe(WidgetInstance);
  });

  // Two roots, not one: a module has endpoints of its own, which is what lets several apps mount the same
  // one. Everything else travels inside whichever of the two carries it.
  it('makes the app and the module definition the aggregate roots of the graph', () => {
    expect(descriptorOf(APP_DEFINITION_ENTITY_NAME).isEmbedded).toBe(false);
    expect(descriptorOf(MODULE_DEFINITION_ENTITY_NAME).isEmbedded).toBe(false);
    [APP_REGION_ENTITY_NAME, APP_ROUTE_ENTITY_NAME, APP_MODULE_MOUNT_ENTITY_NAME, APP_NAV_ITEM_ENTITY_NAME, APP_WIDGET_ENTITY_NAME].forEach((entityName) => {
      expect(descriptorOf(entityName).isEmbedded).toBe(true);
    });
  });

  /**
   * `App Route` is the one level with two owners — an app's `routes` and a module's are the same rows
   * edited by the same descriptor — so the check above that every child names its owner has to hold for
   * both of them. It does only because the route descriptor lists both as `componentParents`.
   */
  it('lets the shared route level name both aggregates that carry it', () => {
    expect(descriptorOf(APP_ROUTE_ENTITY_NAME).isComponentOf(APP_DEFINITION_ENTITY_NAME)).toBe(true);
    expect(descriptorOf(APP_ROUTE_ENTITY_NAME).isComponentOf(MODULE_DEFINITION_ENTITY_NAME)).toBe(true);
  });

  it('binds a store to every descriptor, which is what an embedded list reads its rows from', () => {
    Object.keys(BASE_APP_ENTITY_FACADES).forEach((entityName) => expect(descriptorOf(entityName).store).toBeDefined());
  });

  /**
   * The invariant the runtime enforces by throwing on first render: `EmbeddedComponentsListComponent`
   * resolves its rows through the registry, so a child named by an `EMBEDDED_COMPONENTS` attribute but
   * missing from the map is a form that renders and then fails. Asserting it over the whole graph is what
   * keeps a newly nested definition from being half-wired.
   */
  it('registers every entity an embedded attribute of the graph names', () => {
    Object.keys(BASE_APP_ENTITY_FACADES).forEach((entityName) => {
      descriptorOf(entityName)
        .embeddedAttrDescriptors()
        .forEach((attrDescriptor) => {
          expect(BASE_APP_ENTITY_FACADES[attrDescriptor.linkedEntityType as string], `'${entityName}.${attrDescriptor.attrName}' -> '${attrDescriptor.linkedEntityType}'`).toBeDefined();
        });
    });
  });

  /** The mirror of the check above: a child has to name the entity that carries it, or the control throws. */
  it('lets every embedded child name the owner that carries it', () => {
    Object.keys(BASE_APP_ENTITY_FACADES).forEach((entityName) => {
      descriptorOf(entityName)
        .embeddedAttrDescriptors()
        .forEach((attrDescriptor) => {
          expect(descriptorOf(attrDescriptor.linkedEntityType as string).isComponentOf(entityName)).toBe(true);
        });
    });
  });
});
