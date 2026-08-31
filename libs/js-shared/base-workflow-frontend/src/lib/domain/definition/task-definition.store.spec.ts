import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { TaskDefinitionStore } from './task-definition.store';
import { OTHER_TASK_DEFINITION_DTO, TASK_DEFINITION_DTO } from './test-task-definition';

describe('TaskDefinitionStore', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let store: InstanceType<typeof TaskDefinitionStore>;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: serviceRoot } } }],
    });
    store = TestBed.inject(TaskDefinitionStore);
    controller = TestBed.inject(HttpTestingController);
    controller.expectOne(`${serviceRoot}/tasks`).flush([TASK_DEFINITION_DTO, OTHER_TASK_DEFINITION_DTO]);
  });

  it('loads the tasks of the organization on init', () => {
    expect(store.entities().map((entity) => entity.id)).toEqual(['review-order', 'approve-shipment']);
  });

  // The nested rows are what the embedded route branches list, so they have to survive the load —
  // there is no second request that could fetch them.
  it('exposes a task with its roles flat and its nested rows intact', () => {
    store.setCurrentEntity('review-order');

    expect(store.currentEntity()?.performedByRoles).toEqual(['clerk', 'manager']);
    expect(store.currentEntity()?.inputs).toHaveLength(1);
    expect(store.currentEntity()?.steps).toHaveLength(1);
  });
});
