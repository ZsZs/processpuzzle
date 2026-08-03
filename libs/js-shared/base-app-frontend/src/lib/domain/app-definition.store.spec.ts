import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { AppDefinitionStatus } from './app-definition';
import { AppDefinitionStore } from './app-definition.store';
import { APP_DEFINITION_DTO, OTHER_APP_DEFINITION_DTO, pageOfAppDefinitions } from './test-app-definition';

describe('AppDefinitionStore', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let store: InstanceType<typeof AppDefinitionStore>;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { APP_SERVICE_ROOT: serviceRoot } } }],
    });
    store = TestBed.inject(AppDefinitionStore);
    controller = TestBed.inject(HttpTestingController);
    // The store loads its list on init; answering it here gives the publish tests an entity to work
    // with. The payload is the page of complete definitions the backend answers with — the form
    // reads `currentEntity` straight out of this list, so anything the list drops is lost.
    controller.expectOne(`${serviceRoot}/app-definitions`).flush(pageOfAppDefinitions(APP_DEFINITION_DTO, OTHER_APP_DEFINITION_DTO));
    store.setCurrentEntity('demo');
  });

  it('exposes the whole definition graph as current entity, not just its header fields', () => {
    expect(store.currentEntity()?.regions).toEqual(APP_DEFINITION_DTO.regions);
    expect(store.currentEntity()?.pages).toEqual(APP_DEFINITION_DTO.pages);
    expect(store.currentEntity()?.preset).toBe('sidenav-left');
    expect(store.currentEntity()?.contentMaxWidth).toBe('1280px');
  });

  it('replaces the published definition in the list and as current entity', async () => {
    const pending = store.publish('demo');

    controller.expectOne(`${serviceRoot}/app-definitions/demo/publish`).flush({ id: 'demo', name: 'Demo', status: 'PUBLISHED', version: 3, publishedVersion: 3 });
    await pending;

    expect(store.entities()[0].status).toBe(AppDefinitionStatus.PUBLISHED);
    expect(store.currentEntity()?.publishedVersion).toBe(3);
    expect(store.isLoading()).toBe(false);
    expect(store.error()).toBeUndefined();
  });

  it('leaves the current entity alone when another definition is published', async () => {
    const pending = store.publish('other');

    controller.expectOne(`${serviceRoot}/app-definitions/other/publish`).flush({ id: 'other', name: 'Other', status: 'PUBLISHED', version: 1, publishedVersion: 1 });
    await pending;

    expect(store.entities()[1].status).toBe(AppDefinitionStatus.PUBLISHED);
    expect(store.currentEntity()?.id).toBe('demo');
    expect(store.currentEntity()?.status).toBe(AppDefinitionStatus.DRAFT);
  });

  it('reports a rejected publish through the error state instead of throwing', async () => {
    const pending = store.publish('demo');

    controller.expectOne(`${serviceRoot}/app-definitions/demo/publish`).flush({ message: 'not publishable' }, { status: 400, statusText: 'Bad Request' });

    expect(await pending).toBeUndefined();
    expect(store.error()).toBeTruthy();
    expect(store.entities()[0].status).toBe(AppDefinitionStatus.DRAFT);
    expect(store.isLoading()).toBe(false);
  });
});
