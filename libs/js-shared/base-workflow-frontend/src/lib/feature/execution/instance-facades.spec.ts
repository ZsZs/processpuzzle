import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ProcessInstance, StepResult, TaskInstance, ArtifactInstance } from '../../domain/execution/process-instance';
import { ProcessInstanceMapper } from '../../domain/execution/process-instance.mapper';
import { ProcessInstanceService } from '../../domain/execution/process-instance.service';
import { ProcessInstanceStore } from '../../domain/execution/process-instance.store';
import { ProcessInstanceFacade } from './process-instance.facade';
import { TaskInstanceFacade, TaskStepResultFacade, ArtifactInstanceFacade } from './instance-embedded.facades';

describe('the execution-layer facades', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: 'http://localhost:3000/organizations/processpuzzle-testbed' } } },
        ProcessInstanceFacade,
        TaskInstanceFacade,
        ArtifactInstanceFacade,
        TaskStepResultFacade,
      ],
    });
  });

  it('registers the run under the entity name the route derives from', () => {
    const facade = TestBed.inject(ProcessInstanceFacade);

    expect(facade.entityType).toBe(ProcessInstance);
    expect(facade.entityName).toBe('Process Instance');
    expect(facade.mapper).toBe(TestBed.inject(ProcessInstanceMapper));
    expect(facade.service).toBe(TestBed.inject(ProcessInstanceService));
    expect(facade.storeClass).toBe(ProcessInstanceStore);
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
    const allFacades: Array<Type<{ descriptor: BaseEntityDescriptor }>> = [ProcessInstanceFacade, ...embeddedFacades];

    allFacades.forEach((facadeClass) => expect(TestBed.inject(facadeClass).descriptor.isAbstract).toBe(true));
    embeddedFacades.forEach((facadeClass) => expect(TestBed.inject(facadeClass).descriptor.isEmbedded).toBe(true));
  });
});
