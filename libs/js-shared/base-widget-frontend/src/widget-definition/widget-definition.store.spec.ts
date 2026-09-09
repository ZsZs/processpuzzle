import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { OTHER_WIDGET_DEFINITION_DTO, pageOfWidgetDefinitions, WIDGET_DEFINITION_DTO } from './test-widget-definition';
import { WidgetDefinitionStore } from './widget-definition.store';

describe('WidgetDefinitionStore', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let store: InstanceType<typeof WidgetDefinitionStore>;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { APP_SERVICE_ROOT: serviceRoot } } }],
    });
    store = TestBed.inject(WidgetDefinitionStore);
    controller = TestBed.inject(HttpTestingController);
    // The store loads its list on init; answering it here gives the publish tests an entity to work with.
    controller.expectOne(`${serviceRoot}/widget-definitions`).flush(pageOfWidgetDefinitions(WIDGET_DEFINITION_DTO, OTHER_WIDGET_DEFINITION_DTO));
    store.setCurrentEntity('cards-grid');
  });

  /** The form reads `currentEntity` out of the list, so anything the list drops is lost on the next save. */
  it('exposes the whole definition as current entity, ports and schema included', () => {
    expect(store.currentEntity()?.propsSchema).toEqual(WIDGET_DEFINITION_DTO.propsSchema);
    expect(store.currentEntity()?.inputPorts?.[0].name).toBe('items');
    expect(store.currentEntity()?.outputPorts?.[0].name).toBe('selected');
  });

  it('replaces the published definition in the list and as current entity', async () => {
    const pending = store.publish('cards-grid');

    controller.expectOne(`${serviceRoot}/widget-definitions/cards-grid/publish`).flush({ ...WIDGET_DEFINITION_DTO, status: 'PUBLISHED', version: 4, publishedVersion: 4 });
    await pending;

    expect(store.entities()[0].publishedVersion).toBe(4);
    expect(store.currentEntity()?.status).toBe('PUBLISHED');
    expect(store.isLoading()).toBe(false);
    expect(store.error()).toBeUndefined();
  });

  it('surfaces a refused publish as an error rather than throwing', async () => {
    const pending = store.publish('cards-grid');

    controller.expectOne(`${serviceRoot}/widget-definitions/cards-grid/publish`).flush({ message: 'not publishable' }, { status: 400, statusText: 'Bad Request' });

    expect(await pending).toBeUndefined();
    expect(store.error()).toBeDefined();
    expect(store.isLoading()).toBe(false);
    expect(store.entities()[0].status).toBe('PUBLISHED');
  });

  it('leaves the list alone when publishing something it does not hold', async () => {
    const pending = store.publish('unknown-widget');

    controller.expectOne(`${serviceRoot}/widget-definitions/unknown-widget/publish`).flush({ ...WIDGET_DEFINITION_DTO, key: 'unknown-widget' });
    await pending;

    expect(store.entities().map((entity) => entity.id)).toEqual(['cards-grid', 'entity-grid']);
    expect(store.currentEntity()?.id).toBe('cards-grid');
  });
});
