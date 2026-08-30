import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { createStepDefinitionDescriptor, TASK_STEP_DEFINITION_ID_FIELD } from './step-definition.descriptors';
import { TASK_DEFINITION_ENTITY_NAME, TASK_STEP_DEFINITION_ENTITY_NAME, TOOL_DEFINITION_ENTITY_NAME } from '../workflow-entity-names';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createStepDefinitionDescriptor', () => {
  const descriptor = createStepDefinitionDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('is an embedded component of the task, two levels below the workflow', () => {
    expect(descriptor.entityName).toBe(TASK_STEP_DEFINITION_ENTITY_NAME);
    expect(descriptor.componentParents).toEqual([TASK_DEFINITION_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_workflow.task_step_definition');
  });

  it('describes the step, the tool it may call and the two mappings around that call', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['id', 'name', 'description', 'toolId', 'toolOperation', 'inputMapping', 'outputMapping']);
  });

  it('is identified by its own id', () => {
    expect(descriptor.componentIdentification()).toBe(TASK_STEP_DEFINITION_ID_FIELD);
    expect(byName('id')?.isHeading).toBe(true);
  });

  // A Tool Definition is a routable aggregate of this library, so this reference is one the framework can
  // resolve and navigate to — unlike the cross-feature ids elsewhere in the graph.
  it('resolves the tool against this library’s own aggregate', () => {
    expect(byName('toolId')?.formControlType).toBe(FormControlType.FOREIGN_KEY);
    expect(byName('toolId')?.linkedEntityType).toBe(TOOL_DEFINITION_ENTITY_NAME);
  });

  // The options would have to come from the store of *the tool chosen above*, which a descriptor's
  // static selectables cannot express.
  it('leaves the operation as plain text, its options depending on the chosen tool', () => {
    expect(byName('toolOperation')?.formControlType).toBe(FormControlType.TEXT_BOX);
  });

  it('edits both mappings as open key/value maps', () => {
    expect(byName('inputMapping')?.formControlType).toBe(FormControlType.ADDITIONAL_PROPERTIES);
    expect(byName('outputMapping')?.formControlType).toBe(FormControlType.ADDITIONAL_PROPERTIES);
    expect(byName('inputMapping')?.hideInTable).toBe(true);
    expect(byName('outputMapping')?.hideInTable).toBe(true);
  });

  it('declares no embedded children — a step is a leaf', () => {
    expect(descriptor.embeddedAttrDescriptors()).toEqual([]);
  });
});
