import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ArtifactDefinitionStore } from './artifact-definition.store';
import { ARTIFACT_DEFINITION_DTO, OTHER_ARTIFACT_DEFINITION_DTO } from './test-artifact-definition';

describe('ArtifactDefinitionStore', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let store: InstanceType<typeof ArtifactDefinitionStore>;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: serviceRoot } } }],
    });
    store = TestBed.inject(ArtifactDefinitionStore);
    controller = TestBed.inject(HttpTestingController);
    controller.expectOne(`${serviceRoot}/artifacts`).flush([ARTIFACT_DEFINITION_DTO, OTHER_ARTIFACT_DEFINITION_DTO]);
  });

  it('loads the artifacts of the organization on init', () => {
    expect(store.entities().map((entity) => entity.id)).toEqual(['order-entity', 'fulfillment-invoice']);
  });

  it('exposes an artifact with its type and its bindings intact', () => {
    store.setCurrentEntity('order-entity');

    expect(store.currentEntity()?.type).toBe('ENTITY');
    expect(store.currentEntity()?.stateMachineId).toBe('order');
  });
});
