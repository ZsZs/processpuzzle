import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { WorkflowInstanceStore } from './workflow-instance.store';
import { OTHER_WORKFLOW_INSTANCE_DTO, pageOfWorkflowInstances, WORKFLOW_INSTANCE_DTO } from './test-workflow-instance';

describe('WorkflowInstanceStore', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let store: InstanceType<typeof WorkflowInstanceStore>;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: serviceRoot } } }],
    });
    store = TestBed.inject(WorkflowInstanceStore);
    controller = TestBed.inject(HttpTestingController);
    controller.expectOne(`${serviceRoot}/instances`).flush(pageOfWorkflowInstances(WORKFLOW_INSTANCE_DTO, OTHER_WORKFLOW_INSTANCE_DTO));
  });

  it('loads the runs of the organization on init', () => {
    expect(store.entities()).toHaveLength(2);
    expect(store.entities().map((entity) => entity.status)).toEqual(['ACTIVE', 'COMPLETED']);
  });

  // The store is the stock one even though the screens above it are read-only: what makes them read-only
  // is the descriptor and the attributes, not a narrower store — which is what lets a later assign /
  // complete / skip surface reload a row through the same store the list reads from.
  it('exposes the whole run as current entity', () => {
    store.setCurrentEntity('8f14e45f-ceea-467a-9c9b-9b0c1f0f5a01');

    expect(store.currentEntity()?.workflowName).toBe('Order Fulfillment Workflow');
    expect(store.currentEntity()?.tasks).toHaveLength(3);
    expect(store.currentEntity()?.artifacts[0].currentState).toBe('CONFIRMED');
  });
});
