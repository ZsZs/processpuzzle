import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { createStepResultDescriptor, TASK_STEP_RESULT_ID_FIELD } from './step-result.descriptors';
import { TASK_INSTANCE_ENTITY_NAME, TASK_STEP_RESULT_ENTITY_NAME } from '../workflow-entity-names';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createStepResultDescriptor', () => {
  const descriptor = createStepResultDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('is an embedded, read-only component of the task instance', () => {
    expect(descriptor.entityName).toBe(TASK_STEP_RESULT_ENTITY_NAME);
    expect(descriptor.componentParents).toEqual([TASK_INSTANCE_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
    expect(descriptor.isAbstract).toBe(true);
    expect(attrs.every((attr) => attr.disabled)).toBe(true);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_workflow.task_step_result');
  });

  it('describes the outcome of one step', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['stepId', 'completedAt', 'error', 'toolResponse']);
  });

  // The contract gives a step result no id; the step it reports on is what identifies it.
  it('is identified by the step it reports on', () => {
    expect(descriptor.componentIdentification()).toBe(TASK_STEP_RESULT_ID_FIELD);
    expect(TASK_STEP_RESULT_ID_FIELD).toBe('stepId');
    expect(byName('stepId')?.isHeading).toBe(true);
  });

  it('shows the raw tool response as an open key/value map', () => {
    expect(byName('toolResponse')?.formControlType).toBe(FormControlType.ADDITIONAL_PROPERTIES);
    expect(byName('toolResponse')?.hideInTable).toBe(true);
  });

  it('shows the error in the table, it being the reason to look at all', () => {
    expect(byName('error')?.formControlType).toBe(FormControlType.TEXTAREA);
    expect(byName('error')?.hideInTable).toBe(false);
  });
});
