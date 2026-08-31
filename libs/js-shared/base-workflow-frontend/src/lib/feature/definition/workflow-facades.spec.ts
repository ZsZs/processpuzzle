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
import { ArtifactUse, RequiredStartArtifact, RoleUse, ToolUse, Workflow, WorkflowTaskAssignment } from '../../domain/definition/workflow';
import { WorkflowMapper } from '../../domain/definition/workflow.mapper';
import { WorkflowService } from '../../domain/definition/workflow.service';
import { WorkflowStore } from '../../domain/definition/workflow.store';
import { RoleDefinition } from '../../domain/definition/role-definition';
import { RoleDefinitionMapper } from '../../domain/definition/role-definition.mapper';
import { RoleDefinitionService } from '../../domain/definition/role-definition.service';
import { RoleDefinitionStore } from '../../domain/definition/role-definition.store';
import { StepDefinition, TaskDefinition } from '../../domain/definition/task-definition';
import { TaskDefinitionMapper } from '../../domain/definition/task-definition.mapper';
import { TaskDefinitionService } from '../../domain/definition/task-definition.service';
import { TaskDefinitionStore } from '../../domain/definition/task-definition.store';
import { ToolDefinition, ToolOperation } from '../../domain/definition/tool-definition';
import { ToolDefinitionMapper } from '../../domain/definition/tool-definition.mapper';
import { ToolDefinitionService } from '../../domain/definition/tool-definition.service';
import { ToolDefinitionStore } from '../../domain/definition/tool-definition.store';
import { ArtifactDefinitionFacade } from './artifact-definition.facade';
import { WorkflowFacade } from './workflow.facade';
import { WorkflowRoleDefinitionFacade } from './role-definition.facade';
import { ROLE_MODELER_TAB } from './role-modeler-tab';
import { TaskDefinitionFacade } from './task-definition.facade';
import { ToolDefinitionFacade } from './tool-definition.facade';
import {
  WorkflowArtifactUseFacade,
  WorkflowRequiredStartArtifactFacade,
  WorkflowRoleUseFacade,
  WorkflowTaskAssignmentFacade,
  WorkflowToolUseFacade,
  TaskStepDefinitionFacade,
  ToolOperationFacade,
} from './workflow-embedded.facades';

describe('the definition-layer facades', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: 'http://localhost:3000/organizations/processpuzzle-testbed' } } },
        WorkflowFacade,
        WorkflowRoleDefinitionFacade,
        ArtifactDefinitionFacade,
        TaskDefinitionFacade,
        ToolDefinitionFacade,
        WorkflowTaskAssignmentFacade,
        WorkflowRoleUseFacade,
        WorkflowArtifactUseFacade,
        WorkflowToolUseFacade,
        WorkflowRequiredStartArtifactFacade,
        TaskStepDefinitionFacade,
        ToolOperationFacade,
      ],
    });
  });

  describe('WorkflowFacade', () => {
    it('registers under the entity name the route and the transloco scope derive from', () => {
      const facade = TestBed.inject(WorkflowFacade);

      expect(facade.entityType).toBe(Workflow);
      expect(facade.entityName).toBe('Workflow');
    });

    it('reuses the root-provided mapper, service and store', () => {
      const facade = TestBed.inject(WorkflowFacade);

      expect(facade.mapper).toBe(TestBed.inject(WorkflowMapper));
      expect(facade.service).toBe(TestBed.inject(WorkflowService));
      expect(facade.storeClass).toBe(WorkflowStore);
      expect(facade.store).toBe(TestBed.inject(WorkflowStore));
    });

    it('binds the store to the descriptor it hands out', () => {
      const facade = TestBed.inject(WorkflowFacade);

      expect(facade.descriptor.store).toBe(facade.store);
      expect(facade.attrDescriptors.length).toBeGreaterThan(0);
    });

    // No third screen yet. The Workflows perspective of the modeler goes here when it lands, the same way
    // the Roles one already sits on the role facade: one constant handed both to `baseEntityRoutes` and to
    // the descriptor, so link and route cannot drift.
    it('declares no extra tab yet', () => {
      expect(TestBed.inject(WorkflowFacade).descriptor.extraTabs).toEqual([]);
    });
  });

  // The four catalog aggregates. Each has a mapper, a service against its own endpoint and a store of
  // its own — which is exactly what a role and an artifact did *not* have before the reference model,
  // and what makes them reachable from a list screen rather than only from inside a workflow.
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

    /**
     * The descriptor is the one place `BaseEntityTabsComponent` reads tabs from, so this is what renders
     * the Modeler link — the route alone would leave the screen reachable only by typing its URL.
     */
    it('give the role the Role Modeler tab beside its generic screens', () => {
      expect(TestBed.inject(WorkflowRoleDefinitionFacade).descriptor.extraTabs).toEqual([ROLE_MODELER_TAB]);
    });

    it('leave the other three catalogs with no extra tab', () => {
      const others: Array<Type<{ descriptor: BaseEntityDescriptor }>> = [ArtifactDefinitionFacade, TaskDefinitionFacade, ToolDefinitionFacade];

      others.forEach((facadeClass) => expect(TestBed.inject(facadeClass).descriptor.extraTabs).toEqual([]));
    });

    it('declare none of them embedded', () => {
      const catalogFacades: Array<Type<{ descriptor: BaseEntityDescriptor }>> = [WorkflowRoleDefinitionFacade, ArtifactDefinitionFacade, TaskDefinitionFacade, ToolDefinitionFacade];

      catalogFacades.forEach((facadeClass) => expect(TestBed.inject(facadeClass).descriptor.isEmbedded).toBeFalsy());
    });
  });

  describe('the embedded facades', () => {
    it('each name their own entity and entity type', () => {
      expect(TestBed.inject(WorkflowTaskAssignmentFacade).entityName).toBe('Workflow Task Assignment');
      expect(TestBed.inject(WorkflowTaskAssignmentFacade).entityType).toBe(WorkflowTaskAssignment);
      expect(TestBed.inject(TaskStepDefinitionFacade).entityType).toBe(StepDefinition);
      expect(TestBed.inject(ToolOperationFacade).entityType).toBe(ToolOperation);
    });

    // The three `*Use` rows are the same shape over a different target, and that is exactly why each
    // needs a facade of its own: a facade's store is keyed by the descriptor's entity name, and `roles`,
    // `artifacts` and `tools` are three lists whose rows have to stay apart.
    it('give each Use row its own entity, model class and store', () => {
      const role = TestBed.inject(WorkflowRoleUseFacade);
      const artifact = TestBed.inject(WorkflowArtifactUseFacade);
      const tool = TestBed.inject(WorkflowToolUseFacade);

      expect(role.entityType).toBe(RoleUse);
      expect(artifact.entityType).toBe(ArtifactUse);
      expect(tool.entityType).toBe(ToolUse);
      expect(role.entityName).toBe('Workflow Role Use');
      expect(artifact.entityName).toBe('Workflow Artifact Use');
      expect(tool.entityName).toBe('Workflow Tool Use');
      expect(new Set([role.store, artifact.store, tool.store]).size).toBe(3);
    });

    it('give the start condition its required artifacts', () => {
      const facade = TestBed.inject(WorkflowRequiredStartArtifactFacade);

      expect(facade.entityType).toBe(RequiredStartArtifact);
      expect(facade.entityName).toBe('Workflow Required Start Artifact');
    });

    it('declare themselves embedded, so the framework reads them out of the owner’s payload', () => {
      // Typed as the one thing every facade in the list has in common: each is a different
      // `EmbeddedEntityFacade<T>`, so an inferred array would be a union no `inject` overload accepts.
      const embeddedFacades: Array<Type<{ descriptor: BaseEntityDescriptor }>> = [
        WorkflowTaskAssignmentFacade,
        WorkflowRoleUseFacade,
        WorkflowArtifactUseFacade,
        WorkflowToolUseFacade,
        WorkflowRequiredStartArtifactFacade,
        TaskStepDefinitionFacade,
        ToolOperationFacade,
      ];

      embeddedFacades.forEach((facadeClass) => expect(TestBed.inject(facadeClass).descriptor.isEmbedded).toBe(true));
    });
  });
});
