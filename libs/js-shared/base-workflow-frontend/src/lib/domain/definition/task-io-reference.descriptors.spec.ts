import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { createTaskInputReferenceDescriptor, createTaskOutputReferenceDescriptor, TASK_IO_REFERENCE_ID_FIELD } from './task-io-reference.descriptors';
import { TASK_DEFINITION_ENTITY_NAME, TASK_INPUT_REFERENCE_ENTITY_NAME, TASK_OUTPUT_REFERENCE_ENTITY_NAME } from '../workflow-entity-names';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

/** Both factories produce the same layout, so the shared assertions run over both. */
const bothDirections: Array<[string, () => BaseEntityDescriptor, string, string]> = [
  ['input', createTaskInputReferenceDescriptor, TASK_INPUT_REFERENCE_ENTITY_NAME, 'base_workflow.task_input_reference'],
  ['output', createTaskOutputReferenceDescriptor, TASK_OUTPUT_REFERENCE_ENTITY_NAME, 'base_workflow.task_output_reference'],
];

describe.each(bothDirections)('a task %s reference', (direction, factory, entityName, scopeRoot) => {
  const descriptor = factory();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('is an embedded component of the task, under a name of its own', () => {
    expect(descriptor.entityName).toBe(entityName);
    expect(descriptor.componentParents).toEqual([TASK_DEFINITION_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
  });

  it('has a transloco scope of its own, so the two forms can be labelled differently', () => {
    expect(descriptor.scopeRoot()).toBe(scopeRoot);
    expect(descriptor.i18nKey()).toBe(`${scopeRoot}._self`);
  });

  it('describes the reference kind, the id it points at and the display label', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['type', 'refId', 'label']);
  });

  // The contract gives a reference no id, so `refId` is what identifies it — and what the parent's
  // `referenceIdField` has to name.
  it('is identified by the resource it points at', () => {
    expect(descriptor.componentIdentification()).toBe(TASK_IO_REFERENCE_ID_FIELD);
    expect(byName('refId')?.required).toBe(true);
    expect(byName('refId')?.isHeading).toBe(true);
  });

  it('offers the closed reference-type list as a dropdown', () => {
    expect(byName('type')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(byName('type')?.getSelectables()).toEqual([
      { key: 'BASE_ENTITY', value: 'BASE_ENTITY' },
      { key: 'DOCUMENT', value: 'DOCUMENT' },
      { key: 'WIDGET', value: 'WIDGET' },
    ]);
    expect(byName('type')?.required).toBe(true);
  });

  // Which store a `refId` resolves against depends on the `type` chosen above, so there is no single
  // entity a FOREIGN_KEY could name.
  it('leaves refId as plain text, the target store depending on the type', () => {
    expect(byName('refId')?.formControlType).toBe(FormControlType.TEXT_BOX);
    expect(byName('refId')?.linkedEntityType).toBeUndefined();
  });

  it(`names the direction in the placeholder, which is the only thing the two ${direction} forms differ in`, () => {
    expect(byName('refId')?.placeholder).toContain('this task');
  });
});

describe('the two directions together', () => {
  it('differ only in name, scope and placeholder', () => {
    const input = flatten(createTaskInputReferenceDescriptor().attrDescriptors);
    const output = flatten(createTaskOutputReferenceDescriptor().attrDescriptors);

    expect(output.map((attr) => attr.attrName)).toEqual(input.map((attr) => attr.attrName));
    expect(output.map((attr) => attr.formControlType)).toEqual(input.map((attr) => attr.formControlType));
    expect(output[1].placeholder).not.toBe(input[1].placeholder);
  });
});
