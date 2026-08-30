import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ArtifactDefinition } from '../../domain/definition/artifact-definition';
import { ArtifactDefinitionMapper } from '../../domain/definition/artifact-definition.mapper';
import { ArtifactDefinitionService } from '../../domain/definition/artifact-definition.service';
import { ArtifactDefinitionStore } from '../../domain/definition/artifact-definition.store';
import { ProcessDefinition, ProcessTaskAssignment } from '../../domain/definition/process-definition';
import { ProcessDefinitionMapper } from '../../domain/definition/process-definition.mapper';
import { ProcessDefinitionService } from '../../domain/definition/process-definition.service';
import { ProcessDefinitionStore } from '../../domain/definition/process-definition.store';
import { RoleDefinition } from '../../domain/definition/role-definition';
import { RoleDefinitionMapper } from '../../domain/definition/role-definition.mapper';
import { RoleDefinitionService } from '../../domain/definition/role-definition.service';
import { RoleDefinitionStore } from '../../domain/definition/role-definition.store';
import { StepDefinition, TaskDefinition, TaskIOReference } from '../../domain/definition/task-definition';
import { TaskDefinitionMapper } from '../../domain/definition/task-definition.mapper';
import { TaskDefinitionService } from '../../domain/definition/task-definition.service';
import { TaskDefinitionStore } from '../../domain/definition/task-definition.store';
import { ToolDefinition, ToolOperation } from '../../domain/definition/tool-definition';
import { ToolDefinitionMapper } from '../../domain/definition/tool-definition.mapper';
import { ToolDefinitionService } from '../../domain/definition/tool-definition.service';
import { ToolDefinitionStore } from '../../domain/definition/tool-definition.store';
import { ArtifactDefinitionFacade } from './artifact-definition.facade';
import { ProcessDefinitionFacade } from './process-definition.facade';
import { WorkflowRoleDefinitionFacade } from './role-definition.facade';
import { TaskDefinitionFacade } from './task-definition.facade';
import { ToolDefinitionFacade } from './tool-definition.facade';
import { ProcessTaskAssignmentFacade, TaskInputReferenceFacade, TaskOutputReferenceFacade, TaskStepDefinitionFacade, ToolOperationFacade } from './workflow-embedded.facades';

describe('the definition-layer facades', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: 'http://localhost:3000/organizations/processpuzzle-testbed' } } },
        ProcessDefinitionFacade,
        WorkflowRoleDefinitionFacade,
        ArtifactDefinitionFacade,
        TaskDefinitionFacade,
        ToolDefinitionFacade,
        ProcessTaskAssignmentFacade,
        TaskInputReferenceFacade,
        TaskOutputReferenceFacade,
        TaskStepDefinitionFacade,
        ToolOperationFacade,
      ],
    });
  });

  describe('ProcessDefinitionFacade', () => {
    it('registers under the entity name the route and the transloco scope derive from', () => {
      const facade = TestBed.inject(ProcessDefinitionFacade);

      expect(facade.entityType).toBe(ProcessDefinition);
      expect(facade.entityName).toBe('Process Definition');
    });

    it('reuses the root-provided mapper, service and store', () => {
      const facade = TestBed.inject(ProcessDefinitionFacade);

      expect(facade.mapper).toBe(TestBed.inject(ProcessDefinitionMapper));
      expect(facade.service).toBe(TestBed.inject(ProcessDefinitionService));
      expect(facade.storeClass).toBe(ProcessDefinitionStore);
      expect(facade.store).toBe(TestBed.inject(ProcessDefinitionStore));
    });

    it('binds the store to the descriptor it hands out', () => {
      const facade = TestBed.inject(ProcessDefinitionFacade);

      expect(facade.descriptor.store).toBe(facade.store);
      expect(facade.attrDescriptors.length).toBeGreaterThan(0);
    });

    // No third screen yet. A process modeler would go here, as base-state's does — the same constant
    // handed to `baseEntityRoutes` and to the descriptor, so link and route cannot drift.
    it('declares no extra tab yet', () => {
      expect(TestBed.inject(ProcessDefinitionFacade).descriptor.extraTabs).toEqual([]);
    });
  });

  // The four catalog aggregates. Each has a mapper, a service against its own endpoint and a store of
  // its own — which is exactly what a role and an artifact did *not* have before the reference model,
  // and what makes them reachable from a list screen rather than only from inside a process.
  describe('the catalog facades', () => {
    it('give the role its own mapper, service and store', () => {
      const facade = TestBed.inject(WorkflowRoleDefinitionFacade);

      expect(facade.entityType).toBe(RoleDefinition);
      expect(facade.entityName).toBe('Workflow Role Definition');
      expect(facade.mapper).toBe(TestBed.inject(RoleDefinitionMapper));
      expect(facade.service).toBe(TestBed.inject(RoleDefinitionService));
      expect(facade.storeClass).toBe(RoleDefinitionStore);
    });

    it('give the artifact its own mapper, service and store', () => {
      const facade = TestBed.inject(ArtifactDefinitionFacade);

      expect(facade.entityType).toBe(ArtifactDefinition);
      expect(facade.entityName).toBe('Artifact Definition');
      expect(facade.mapper).toBe(TestBed.inject(ArtifactDefinitionMapper));
      expect(facade.service).toBe(TestBed.inject(ArtifactDefinitionService));
      expect(facade.storeClass).toBe(ArtifactDefinitionStore);
    });

    it('give the task its own mapper, service and store', () => {
      const facade = TestBed.inject(TaskDefinitionFacade);

      expect(facade.entityType).toBe(TaskDefinition);
      expect(facade.entityName).toBe('Task Definition');
      expect(facade.mapper).toBe(TestBed.inject(TaskDefinitionMapper));
      expect(facade.service).toBe(TestBed.inject(TaskDefinitionService));
      expect(facade.storeClass).toBe(TaskDefinitionStore);
    });

    it('leave the tool as the aggregate it always was', () => {
      const facade = TestBed.inject(ToolDefinitionFacade);

      expect(facade.entityType).toBe(ToolDefinition);
      expect(facade.entityName).toBe('Tool Definition');
      expect(facade.mapper).toBe(TestBed.inject(ToolDefinitionMapper));
      expect(facade.service).toBe(TestBed.inject(ToolDefinitionService));
      expect(facade.storeClass).toBe(ToolDefinitionStore);
    });

    it('declare none of the four embedded', () => {
      const catalogFacades: Array<Type<{ descriptor: BaseEntityDescriptor }>> = [WorkflowRoleDefinitionFacade, ArtifactDefinitionFacade, TaskDefinitionFacade, ToolDefinitionFacade];

      catalogFacades.forEach((facadeClass) => expect(TestBed.inject(facadeClass).descriptor.isEmbedded).toBeFalsy());
    });
  });

  describe('the embedded facades', () => {
    it('each name their own entity and entity type', () => {
      expect(TestBed.inject(ProcessTaskAssignmentFacade).entityName).toBe('Process Task Assignment');
      expect(TestBed.inject(ProcessTaskAssignmentFacade).entityType).toBe(ProcessTaskAssignment);
      expect(TestBed.inject(TaskStepDefinitionFacade).entityType).toBe(StepDefinition);
      expect(TestBed.inject(ToolOperationFacade).entityType).toBe(ToolOperation);
    });

    // Inputs and outputs share `TaskIOReference` and differ only in their descriptor. That is also why
    // they cannot share a facade: a facade's store is keyed by the descriptor's entity name, and the two
    // lists' rows have to stay apart.
    it('keep the two task-reference directions apart despite sharing a model class', () => {
      const input = TestBed.inject(TaskInputReferenceFacade);
      const output = TestBed.inject(TaskOutputReferenceFacade);

      expect(input.entityType).toBe(TaskIOReference);
      expect(output.entityType).toBe(TaskIOReference);
      expect(input.entityName).toBe('Task Input Reference');
      expect(output.entityName).toBe('Task Output Reference');
      expect(output.store).not.toBe(input.store);
    });

    it('declare themselves embedded, so the framework reads them out of the owner’s payload', () => {
      // Typed as the one thing every facade in the list has in common: each is a different
      // `EmbeddedEntityFacade<T>`, so an inferred array would be a union no `inject` overload accepts.
      const embeddedFacades: Array<Type<{ descriptor: BaseEntityDescriptor }>> = [
        ProcessTaskAssignmentFacade,
        TaskInputReferenceFacade,
        TaskOutputReferenceFacade,
        TaskStepDefinitionFacade,
        ToolOperationFacade,
      ];

      embeddedFacades.forEach((facadeClass) => expect(TestBed.inject(facadeClass).descriptor.isEmbedded).toBe(true));
    });
  });
});
