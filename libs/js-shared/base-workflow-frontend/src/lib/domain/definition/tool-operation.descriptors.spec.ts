import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { createToolOperationDescriptor, TOOL_OPERATION_ID_FIELD } from './tool-operation.descriptors';
import { TOOL_DEFINITION_ENTITY_NAME, TOOL_OPERATION_ENTITY_NAME } from '../workflow-entity-names';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createToolOperationDescriptor', () => {
  const descriptor = createToolOperationDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('is an embedded component of the tool', () => {
    expect(descriptor.entityName).toBe(TOOL_OPERATION_ENTITY_NAME);
    expect(descriptor.componentParents).toEqual([TOOL_DEFINITION_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_workflow.tool_operation');
  });

  it('describes the call: a method, a path, a body template and the codes that mean success', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['id', 'method', 'path', 'expectedStatusCodes', 'description', 'payloadTemplate']);
  });

  it('is identified by its own id, which is what a step’s toolOperation names', () => {
    expect(descriptor.componentIdentification()).toBe(TOOL_OPERATION_ID_FIELD);
    expect(byName('id')?.isHeading).toBe(true);
  });

  it('offers the closed HTTP method list as a required dropdown', () => {
    expect(byName('method')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(byName('method')?.getSelectables()?.map((selectable) => selectable.value)).toEqual(['GET', 'POST', 'PUT', 'PATCH', 'DELETE']);
    expect(byName('method')?.required).toBe(true);
    expect(byName('path')?.required).toBe(true);
  });

  // `integer[]` on the wire. TAGS is the only control that edits a flat list and it emits strings, so
  // the model holds strings and the mapper converts — see ToolDefinitionMapper.
  it('edits the status codes as chips, there being no numeric-array control', () => {
    expect(byName('expectedStatusCodes')?.formControlType).toBe(FormControlType.TAGS);
    expect(byName('expectedStatusCodes')?.hideInTable).toBe(true);
  });

  it('gives the body template a textarea and keeps it out of the table', () => {
    expect(byName('payloadTemplate')?.formControlType).toBe(FormControlType.TEXTAREA);
    expect(byName('payloadTemplate')?.hideInTable).toBe(true);
  });
});
