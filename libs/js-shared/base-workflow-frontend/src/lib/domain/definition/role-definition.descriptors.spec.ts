import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { createRoleDefinitionDescriptor } from './role-definition.descriptors';
import { WORKFLOW_ROLE_DEFINITION_ENTITY_NAME } from '../workflow-entity-names';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createRoleDefinitionDescriptor', () => {
  const descriptor = createRoleDefinitionDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  // A catalog aggregate now: no `componentParent`, no `isEmbedded`. The name stays prefixed, because
  // BASE_ENTITY_FACADE_REGISTRY is one flat map for the whole application and a bare `Role Definition`
  // is a name a tenant's own metadata could plausibly claim.
  it('is a standalone aggregate under a prefixed name', () => {
    expect(descriptor.entityName).toBe(WORKFLOW_ROLE_DEFINITION_ENTITY_NAME);
    expect(descriptor.entityName).toBe('Workflow Role Definition');
    expect(descriptor.componentParents).toEqual([]);
    expect(descriptor.isEmbedded).toBeFalsy();
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_workflow.workflow_role_definition');
  });

  it('describes the role, its link into base-entity’s registry and its revision', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['id', 'name', 'entityRoleId', 'version', 'updatedAt', 'description', 'responsibleFor']);
  });

  // `/roles/{roleId}` addresses a role by the author-chosen id, so the list column has to open it and
  // the form has to name it.
  it('is identified by its own id, which every referencing attribute names', () => {
    expect(descriptor.componentIdentification()).toBe('id');
    expect(byName('id')?.required).toBe(true);
    expect(byName('id')?.isHeading).toBe(true);
    expect(byName('id')?.isLinkToDetails).toBe(true);
  });

  // base-entity's role registry is a tenant's data, which this library has no store for — the contract
  // is explicit that base-workflow references another feature by id only.
  it('leaves the base-entity role as plain text rather than a picker', () => {
    expect(byName('entityRoleId')?.formControlType).toBe(FormControlType.TEXT_BOX);
    expect(byName('entityRoleId')?.linkedEntityType).toBeUndefined();
  });

  it('shows the server-assigned fields without letting them be edited', () => {
    expect(byName('version')?.disabled).toBe(true);
    expect(byName('updatedAt')?.disabled).toBe(true);
  });

  it('keeps the description out of the table', () => {
    expect(byName('description')?.formControlType).toBe(FormControlType.TEXTAREA);
    expect(byName('description')?.hideInTable).toBe(true);
  });
});
