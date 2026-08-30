import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ProcessDefinitionStore } from './process-definition.store';
import { OTHER_PROCESS_DEFINITION_DTO, pageOfProcessDefinitions, PROCESS_DEFINITION_DTO } from './test-process-definition';

describe('ProcessDefinitionStore', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let store: InstanceType<typeof ProcessDefinitionStore>;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: serviceRoot } } }],
    });
    store = TestBed.inject(ProcessDefinitionStore);
    controller = TestBed.inject(HttpTestingController);
    controller.expectOne(`${serviceRoot}/processes`).flush(pageOfProcessDefinitions(PROCESS_DEFINITION_DTO, OTHER_PROCESS_DEFINITION_DTO));
  });

  it('loads the processes of the organization on init', () => {
    expect(store.entities()).toHaveLength(2);
    expect(store.entities().map((entity) => entity.id)).toEqual(['order-fulfillment-workflow', 'claim-handling-workflow']);
  });

  it('selects a process by the id a details link carries', () => {
    store.setCurrentEntity('order-fulfillment-workflow');

    expect(store.currentEntity()?.name).toBe('Order Fulfillment Workflow');
  });

  // The form reads its entity out of this list rather than re-fetching it, so anything the list dropped
  // would render blank and then be destroyed by the next save.
  it('exposes the whole process as current entity, not just its header fields', () => {
    store.setCurrentEntity('order-fulfillment-workflow');

    expect(store.currentEntity()?.roles).toEqual(['clerk', 'manager']);
    expect(store.currentEntity()?.artifacts).toEqual(['order-entity', 'fulfillment-invoice']);
    expect(store.currentEntity()?.tasks[1].dependsOn).toEqual(['review-order']);
  });
});
