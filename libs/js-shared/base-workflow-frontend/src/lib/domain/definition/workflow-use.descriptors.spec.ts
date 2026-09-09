import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import {
  createWorkflowArtifactUseDescriptor,
  createWorkflowRoleUseDescriptor,
  createWorkflowToolUseDescriptor,
  WORKFLOW_ARTIFACT_USE_ID_FIELD,
  WORKFLOW_ROLE_USE_ID_FIELD,
  WORKFLOW_TOOL_USE_ID_FIELD,
} from './workflow-use.descriptors';
import {
  ARTIFACT_DEFINITION_ENTITY_NAME,
  TOOL_DEFINITION_ENTITY_NAME,
  WORKFLOW_ARTIFACT_USE_ENTITY_NAME,
  WORKFLOW_ENTITY_NAME,
  WORKFLOW_ROLE_DEFINITION_ENTITY_NAME,
  WORKFLOW_ROLE_USE_ENTITY_NAME,
  WORKFLOW_TOOL_USE_ENTITY_NAME,
} from '../workflow-entity-names';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

/**
 * The three are one factory, so they are asserted as one table: the same shape over a different target.
 * `linkedEntityType` on the attribute is the *definition* the row references; `entityName` on the
 * descriptor is the row itself, and the two must not be confused — that distinction is the whole reason a
 * `*Use` is an entity rather than an id.
 */
const uses: Array<[string, () => BaseEntityDescriptor, string, string, string, string]> = [
  ['role', createWorkflowRoleUseDescriptor, WORKFLOW_ROLE_USE_ENTITY_NAME, WORKFLOW_ROLE_USE_ID_FIELD, WORKFLOW_ROLE_DEFINITION_ENTITY_NAME, 'workflow_role_use'],
  ['artifact', createWorkflowArtifactUseDescriptor, WORKFLOW_ARTIFACT_USE_ENTITY_NAME, WORKFLOW_ARTIFACT_USE_ID_FIELD, ARTIFACT_DEFINITION_ENTITY_NAME, 'workflow_artifact_use'],
  ['tool', createWorkflowToolUseDescriptor, WORKFLOW_TOOL_USE_ENTITY_NAME, WORKFLOW_TOOL_USE_ID_FIELD, TOOL_DEFINITION_ENTITY_NAME, 'workflow_tool_use'],
];

describe.each(uses)('the %s Use descriptor', (_label, create, entityName, idField, linkedEntityType, scopeSegment) => {
  const descriptor = create();
  const attrs = flatten(descriptor.attrDescriptors);

  it('is an embedded component of the workflow', () => {
    expect(descriptor.entityName).toBe(entityName);
    expect(descriptor.componentParents).toEqual([WORKFLOW_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe(`base_workflow.${scopeSegment}`);
  });

  // One field, and deliberately so: the contract's `RoleUse` and its two siblings wrap nothing but a
  // definition id today, and the schema is explicit that the object is the extension point for
  // per-workflow configuration rather than an oversight.
  it('carries the one definition id the contract gives it', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual([idField]);
  });

  // A picker, not a text box, and this is the point of modelling a `*Use` as an entity at all: the
  // control resolves the definition's display name through its store, offers the tenant's catalog, and
  // renders a link icon that navigates to the definition itself.
  it('references the catalog definition as a navigable foreign key', () => {
    const [attr] = attrs;

    expect(attr.formControlType).toBe(FormControlType.FOREIGN_KEY);
    expect(attr.linkedEntityType).toBe(linkedEntityType);
    expect(attr.required).toBe(true);
  });

  // The definition it wraps is what identifies the row: the contract gives a `*Use` no key of its own, so
  // the referencing attribute has to name this field rather than leave `referenceIdField` at `id`.
  it('is identified by the definition id rather than by an id of its own', () => {
    const [attr] = attrs;

    expect(descriptor.componentIdentification()).toBe(idField);
    expect(attr.isHeading).toBe(true);
    expect(attr.isLinkToDetails).toBe(true);
  });
});

describe('the three Use descriptors together', () => {
  // They cannot share one entity name even though they share a shape: an `EMBEDDED_COMPONENTS` control
  // resolves its child by name, and `BaseEntityDescriptor.embeddedAttrFor()` refuses a child type carried
  // by two attributes, because the route segment names the entity.
  it('name three distinct entities, the route segment naming each', () => {
    const entityNames = uses.map(([, create]) => create().entityName);

    expect(new Set(entityNames).size).toBe(3);
  });

  it('key each row by a field of its own target', () => {
    expect([WORKFLOW_ROLE_USE_ID_FIELD, WORKFLOW_ARTIFACT_USE_ID_FIELD, WORKFLOW_TOOL_USE_ID_FIELD]).toEqual(['roleDefinitionId', 'artifactDefinitionId', 'toolDefinitionId']);
  });
});
