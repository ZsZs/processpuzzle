import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { BASE_WORKFLOW_ENTITY_FACADES, BASE_WORKFLOW_FACADE_PROVIDERS } from './base-workflow.providers';
import { createArtifactDefinitionDescriptor } from './domain/definition/artifact-definition.descriptors';
import { createProcessDefinitionDescriptor } from './domain/definition/process-definition.descriptors';
import { createProcessTaskAssignmentDescriptor } from './domain/definition/process-task-assignment.descriptors';
import { createRoleDefinitionDescriptor } from './domain/definition/role-definition.descriptors';
import { createStepDefinitionDescriptor } from './domain/definition/step-definition.descriptors';
import { createTaskDefinitionDescriptor } from './domain/definition/task-definition.descriptors';
import { createTaskInputReferenceDescriptor, createTaskOutputReferenceDescriptor } from './domain/definition/task-io-reference.descriptors';
import { createToolDefinitionDescriptor } from './domain/definition/tool-definition.descriptors';
import { createToolOperationDescriptor } from './domain/definition/tool-operation.descriptors';
import { createArtifactInstanceDescriptor } from './domain/execution/artifact-instance.descriptors';
import { createProcessInstanceDescriptor } from './domain/execution/process-instance.descriptors';
import { createStepResultDescriptor } from './domain/execution/step-result.descriptors';
import { createTaskInstanceDescriptor } from './domain/execution/task-instance.descriptors';

/** Every descriptor this library ships, so the registry can be checked against the graph itself. */
const allDescriptors: BaseEntityDescriptor[] = [
  createProcessDefinitionDescriptor(),
  createProcessTaskAssignmentDescriptor(),
  createRoleDefinitionDescriptor(),
  createArtifactDefinitionDescriptor(),
  createTaskDefinitionDescriptor(),
  createTaskInputReferenceDescriptor(),
  createTaskOutputReferenceDescriptor(),
  createStepDefinitionDescriptor(),
  createToolDefinitionDescriptor(),
  createToolOperationDescriptor(),
  createProcessInstanceDescriptor(),
  createTaskInstanceDescriptor(),
  createArtifactInstanceDescriptor(),
  createStepResultDescriptor(),
];

/** The attributes of a descriptor, flattened out of the flexbox rows they are laid out in. */
function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

/** Every entity name a `RELATED_ENTITIES` or `FOREIGN_KEY` attribute of this library points at. */
const referencedNames = new Set(
  allDescriptors
    .flatMap((descriptor) => flatten(descriptor.attrDescriptors))
    .filter((attr) => attr.formControlType === FormControlType.RELATED_ENTITIES || attr.formControlType === FormControlType.FOREIGN_KEY)
    .map((attr) => attr.linkedEntityType)
    .filter((entityName): entityName is string => entityName !== undefined),
);

describe('BASE_WORKFLOW_FACADE_PROVIDERS', () => {
  it('provides one facade per entity of the graph', () => {
    expect(BASE_WORKFLOW_FACADE_PROVIDERS).toHaveLength(allDescriptors.length);
  });

  it('lists exactly the facades the registry is keyed by, with no duplicates', () => {
    expect(new Set(BASE_WORKFLOW_FACADE_PROVIDERS).size).toBe(BASE_WORKFLOW_FACADE_PROVIDERS.length);
    expect(new Set(Object.values(BASE_WORKFLOW_ENTITY_FACADES))).toEqual(new Set(BASE_WORKFLOW_FACADE_PROVIDERS));
  });
});

describe('BASE_WORKFLOW_ENTITY_FACADES', () => {
  it('keys every facade by the entity name its descriptor declares', () => {
    expect(Object.keys(BASE_WORKFLOW_ENTITY_FACADES).sort()).toEqual(allDescriptors.map((descriptor) => descriptor.entityName).sort());
  });

  // A registry miss is not a missing row: the control throws on first render. The reference model makes
  // this load-bearing in a way the embedded one did not — a process's roles, artifacts and tools all
  // resolve through this map now, and so does an assignment's task.
  it('registers every entity the library’s own attributes reference', () => {
    expect(referencedNames.size).toBeGreaterThan(0);
    referencedNames.forEach((entityName) => expect(BASE_WORKFLOW_ENTITY_FACADES[entityName]).toBeDefined());
  });

  it('registers every embedded child its owner names', () => {
    const embeddedNames = new Set(
      allDescriptors.flatMap((descriptor) =>
        descriptor
          .embeddedAttrDescriptors()
          .map((attr) => attr.linkedEntityType)
          .filter((entityName): entityName is string => entityName !== undefined),
      ),
    );

    expect(embeddedNames.size).toBeGreaterThan(0);
    embeddedNames.forEach((entityName) => expect(BASE_WORKFLOW_ENTITY_FACADES[entityName]).toBeDefined());
  });

  // The four catalog aggregates plus the process and the run: each is reachable on its own, so each
  // needs a facade whether or not another entity happens to reference it.
  it('registers each of the six routable aggregates', () => {
    ['Process Definition', 'Workflow Role Definition', 'Artifact Definition', 'Task Definition', 'Tool Definition', 'Process Instance'].forEach((entityName) =>
      expect(BASE_WORKFLOW_ENTITY_FACADES[entityName]).toBeDefined(),
    );
  });

  it('prefixes the ambiguous name, the registry being one flat map for the whole application', () => {
    Object.keys(BASE_WORKFLOW_ENTITY_FACADES).forEach((entityName) => expect(entityName).not.toBe('Role Definition'));
  });
});
