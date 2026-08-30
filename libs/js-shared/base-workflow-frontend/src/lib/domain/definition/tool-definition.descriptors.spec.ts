import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { createToolDefinitionDescriptor } from './tool-definition.descriptors';
import { TOOL_DEFINITION_ENTITY_NAME, TOOL_OPERATION_ENTITY_NAME } from '../workflow-entity-names';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createToolDefinitionDescriptor', () => {
  const descriptor = createToolDefinitionDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('is a routable aggregate of its own, shared across workflows', () => {
    expect(descriptor.entityName).toBe(TOOL_DEFINITION_ENTITY_NAME);
    expect(descriptor.componentParents).toEqual([]);
    expect(descriptor.isEmbedded).toBe(false);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_workflow.tool_definition');
    expect(byName('secretRef')?.i18nKey()).toBe('base_workflow.tool_definition.secretRef');
  });

  // `type` and `secretRef` are the two fields of the contract's nested `auth` object, authored here as
  // siblings because a descriptor addresses a property rather than a path.
  it('authors the flattened auth fields beside the endpoint', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['id', 'name', 'baseUrl', 'type', 'secretRef', 'version', 'createdAt', 'description', 'operations']);
  });

  it('identifies a tool by its author-chosen id', () => {
    expect(byName('id')?.isLinkToDetails).toBe(true);
    expect(byName('id')?.isHeading).toBe(true);
    expect(descriptor.componentIdentification()).toBe('id');
  });

  // `format: uri` becomes a `java.net.URI` field in the generated DTO, so whitespace is rejected at
  // deserialization — before any validator runs, and with a bare 400. Rejecting it in the form is the
  // honest place; it is also what makes the generated e2e fixture emit a token rather than prose.
  it('rejects whitespace in the base URL, which the wire type cannot carry', () => {
    const pattern = byName('baseUrl')?.pattern ?? '';

    expect(byName('baseUrl')?.required).toBe(true);
    expect(pattern).toBe('^\\S+$');
    expect(new RegExp(pattern).test('https://checks.example.com')).toBe(true);
    expect(new RegExp(pattern).test('Tool Base URL e2e')).toBe(false);
  });

  it('offers the closed auth-type list as a dropdown', () => {
    expect(byName('type')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(byName('type')?.getSelectables()?.map((selectable) => selectable.value)).toEqual(['NONE', 'BEARER_TOKEN', 'BASIC', 'API_KEY']);
  });

  it('keeps the secret reference out of the table — it names a secret, even if it is not one', () => {
    expect(byName('secretRef')?.hideInTable).toBe(true);
  });

  it('shows the server-assigned fields without letting them be edited', () => {
    expect(byName('version')?.disabled).toBe(true);
    expect(byName('createdAt')?.disabled).toBe(true);
  });

  it('carries the operations as embedded components addressed by their own id', () => {
    expect(descriptor.embeddedAttrFor(TOOL_OPERATION_ENTITY_NAME)?.attrName).toBe('operations');
    expect(byName('operations')?.formControlType).toBe(FormControlType.EMBEDDED_COMPONENTS);
    expect(byName('operations')?.referenceIdField).toBe('id');
  });
});
