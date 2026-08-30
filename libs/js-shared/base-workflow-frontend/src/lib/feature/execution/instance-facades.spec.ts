import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { WorkflowInstance, StepResult, TaskInstance, ArtifactInstance } from '../../domain/execution/workflow-instance';
import { WorkflowInstanceMapper } from '../../domain/execution/workflow-instance.mapper';
import { WorkflowInstanceService } from '../../domain/execution/workflow-instance.service';
import { WorkflowInstanceStore } from '../../domain/execution/workflow-instance.store';
import { WorkflowInstanceFacade } from './workflow-instance.facade';
import { TaskInstanceFacade, TaskStepResultFacade, ArtifactInstanceFacade } from './instance-embedded.facades';

describe('the execution-layer facades', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: 'http://localhost:3000/organizations/processpuzzle-testbed' } } },
        WorkflowInstanceFacade,
        TaskInstanceFacade,
        ArtifactInstanceFacade,
        TaskStepResultFacade,
      ],
    });
  });

  it('registers the run under the entity name the route derives from', () => {
    const facade = TestBed.inject(WorkflowInstanceFacade);

    expect(facade.entityType).toBe(WorkflowInstance);
    expect(facade.entityName).toBe('Workflow Instance');
    expect(facade.mapper).toBe(TestBed.inject(WorkflowInstanceMapper));
    expect(facade.service).toBe(TestBed.inject(WorkflowInstanceService));
    expect(facade.storeClass).toBe(WorkflowInstanceStore);
  });

  it('names each embedded level and its model class', () => {
    expect(TestBed.inject(TaskInstanceFacade).entityType).toBe(TaskInstance);
    expect(TestBed.inject(ArtifactInstanceFacade).entityType).toBe(ArtifactInstance);
    expect(TestBed.inject(TaskStepResultFacade).entityType).toBe(StepResult);
  });

  // Ordinary facades, even though the screens are read-only: what makes them read-only is their
  // descriptors, which is what lets the rows be listed and deep-linked exactly as an editable child is.
  it('hands out read-only descriptors from otherwise ordinary facades', () => {
    // Typed as the one thing the four have in common: each is a different facade generic, so an inferred
    // array would be a union no `inject` overload accepts.
    const embeddedFacades: Array<Type<{ descriptor: BaseEntityDescriptor }>> = [TaskInstanceFacade, ArtifactInstanceFacade, TaskStepResultFacade];
    const allFacades: Array<Type<{ descriptor: BaseEntityDescriptor }>> = [WorkflowInstanceFacade, ...embeddedFacades];

    allFacades.forEach((facadeClass) => expect(TestBed.inject(facadeClass).descriptor.isAbstract).toBe(true));
    embeddedFacades.forEach((facadeClass) => expect(TestBed.inject(facadeClass).descriptor.isEmbedded).toBe(true));
  });
});
