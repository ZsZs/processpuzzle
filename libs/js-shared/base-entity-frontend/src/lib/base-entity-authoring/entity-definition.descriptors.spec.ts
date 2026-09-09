import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, FormControlType } from '../base-entity/abstact-attr.descriptor';
import type { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { FlexboxDescriptor } from '../base-entity/flexboxDescriptor';
import { ENTITY_DEFINITION_STATUSES, ENTITY_FORM_CONTROL_TYPES, ENTITY_VALUE_KINDS } from '../base-entity-definition/entity-definition';
import { ENTITY_ATTRIBUTE_I18N_SCOPE, ENTITY_DEFINITION_I18N_SCOPE } from '../i18n/base-entity.i18n';
import { createEntityAttributeDescriptor, ENTITY_ATTRIBUTE_ID_FIELD } from './entity-attribute.descriptors';
import { ENTITY_ATTRIBUTE_ENTITY_NAME, ENTITY_DEFINITION_ENTITY_NAME } from './entity-authoring-names';
import { createEntityDefinitionDescriptor } from './entity-definition.descriptors';

/** The leaf attributes of a descriptor, flattened out of the flexbox rows that lay them out. */
function flattenAttrs(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flattenAttrs(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

function attrOf(descriptors: AbstractAttrDescriptor[], attrName: string): BaseEntityAttrDescriptor {
  const attr = flattenAttrs(descriptors).find((candidate) => candidate.attrName === attrName);
  if (!attr) throw new Error(`no attribute '${attrName}'`);
  return attr;
}

describe('createEntityDefinitionDescriptor', () => {
  const descriptor = createEntityDefinitionDescriptor();
  const attrs = descriptor.attrDescriptors;

  it('is the routable entity of the authoring branch', () => {
    expect(descriptor.entityName).toBe(ENTITY_DEFINITION_ENTITY_NAME);
    expect(descriptor.i18nScope).toBe(ENTITY_DEFINITION_I18N_SCOPE);
    expect(descriptor.isEmbedded).toBe(false);
    expect(descriptor.componentParents).toEqual([]);
  });

  it('authors every field of BaseEntityDefinitionInput', () => {
    expect(flattenAttrs(attrs).map((attr) => attr.attrName).sort()).toEqual(['attributes', 'code', 'componentParents', 'description', 'isEmbedded', 'name', 'status', 'updatedAt', 'version']);
  });

  /**
   * `code` is the business key *and* the record's identity — `EntityDefinitionMapper` mirrors it into `id`.
   * `isLinkToDetails` is what makes the list's own column open the definition.
   */
  it('titles the record by its code', () => {
    const codeAttr = attrOf(attrs, 'code');

    expect(codeAttr.isLinkToDetails).toBe(true);
    expect(codeAttr.isHeading).toBe(true);
    expect(codeAttr.required).toBe(true);
  });

  it('requires the entity name the screens are keyed by', () => {
    expect(attrOf(attrs, 'name').required).toBe(true);
  });

  it('offers the contract statuses, so only an ACTIVE definition can be rendered on purpose', () => {
    const statusAttr = attrOf(attrs, 'status');

    expect(statusAttr.formControlType).toBe(FormControlType.DROPDOWN);
    expect(statusAttr.getSelectables()?.map((selectable) => selectable.value)).toEqual([...ENTITY_DEFINITION_STATUSES]);
  });

  /**
   * Containment, not association: the contract nests the attributes in the definition document and gives
   * them no list endpoint, so the rows travel inside this entity's payload and are saved with it.
   */
  it('contains its attributes as an embedded list keyed by code', () => {
    const attributesAttr = attrOf(attrs, 'attributes');

    expect(attributesAttr.formControlType).toBe(FormControlType.EMBEDDED_COMPONENTS);
    expect(attributesAttr.linkedEntityType).toBe(ENTITY_ATTRIBUTE_ENTITY_NAME);
    expect(attributesAttr.referenceIdField).toBe(ENTITY_ATTRIBUTE_ID_FIELD);
  });

  /** Named by code, and the codes on offer are rows of the list this form was opened from. */
  it('edits the component parents as free tags rather than a stale option list', () => {
    const parentsAttr = attrOf(attrs, 'componentParents');

    expect(parentsAttr.formControlType).toBe(FormControlType.TAGS);
    expect(parentsAttr.getSelectables()).toBeUndefined();
  });

  /** `BaseEntityDefinitionInput` has no field to send either back in, so neither may be editable. */
  it('shows the server-assigned revision fields read-only', () => {
    expect(attrOf(attrs, 'version').disabled).toBe(true);
    expect(attrOf(attrs, 'updatedAt').disabled).toBe(true);
  });
});

describe('createEntityAttributeDescriptor', () => {
  const descriptor = createEntityAttributeDescriptor();
  const attrs = descriptor.attrDescriptors;

  it('is embedded in the definition that carries it', () => {
    expect(descriptor.entityName).toBe(ENTITY_ATTRIBUTE_ENTITY_NAME);
    expect(descriptor.i18nScope).toBe(ENTITY_ATTRIBUTE_I18N_SCOPE);
    expect(descriptor.isEmbedded).toBe(true);
    expect(descriptor.componentParents).toEqual([ENTITY_DEFINITION_ENTITY_NAME]);
  });

  /**
   * `description` is deliberately absent: it is on the model but not in `BaseEntityAttributeInput`, so a
   * value typed into a control for it would be dropped by the next save.
   */
  it('authors every field of BaseEntityAttributeInput and nothing the contract would drop', () => {
    expect(flattenAttrs(attrs).map((attr) => attr.attrName).sort()).toEqual([
      'code',
      'defaultValue',
      'displayOrder',
      'enumValues',
      'formControlType',
      'indexed',
      'isLinkToDetails',
      'isMultiValued',
      'linkedEntityType',
      'name',
      'required',
      'valueKind',
    ]);
  });

  it('identifies a row by its code', () => {
    expect(ENTITY_ATTRIBUTE_ID_FIELD).toBe('code');
    expect(attrOf(attrs, 'code').isLinkToDetails).toBe(true);
    expect(attrOf(attrs, 'code').isHeading).toBe(true);
  });

  /** Two fields, because the contract separates what the value *is* from which widget edits it. */
  it('requires both the value kind and the control type, over the contract enums', () => {
    const valueKindAttr = attrOf(attrs, 'valueKind');
    const controlTypeAttr = attrOf(attrs, 'formControlType');

    expect(valueKindAttr.required).toBe(true);
    expect(valueKindAttr.getSelectables()?.map((selectable) => selectable.value)).toEqual([...ENTITY_VALUE_KINDS]);
    expect(controlTypeAttr.required).toBe(true);
    expect(controlTypeAttr.getSelectables()?.map((selectable) => selectable.value)).toEqual([...ENTITY_FORM_CONTROL_TYPES]);
  });

  /**
   * The contract's enum, not the frontend's: `ADDITIONAL_PROPERTIES`, `FLEX_BOX`, `LABEL`, `TAGS` and
   * `TITLE` are rendering concerns a definition cannot name, and the backend would reject them.
   */
  it('offers no control type the backend does not accept', () => {
    const rendererOnly = [FormControlType.ADDITIONAL_PROPERTIES, FormControlType.FLEX_BOX, FormControlType.LABEL, FormControlType.TAGS, FormControlType.TITLE];

    expect(ENTITY_FORM_CONTROL_TYPES.filter((type) => rendererOnly.includes(type as FormControlType))).toEqual([]);
  });

  it('edits the enum options as tags', () => {
    expect(attrOf(attrs, 'enumValues').formControlType).toBe(FormControlType.TAGS);
  });

  /** A sibling aggregate named by code, not a level to descend into — see the route branch. */
  it('names the linked definition as free text', () => {
    expect(attrOf(attrs, 'linkedEntityType').formControlType).toBe(FormControlType.TEXT_BOX);
  });

  it('offers the display order as a number', () => {
    expect(attrOf(attrs, 'displayOrder').options.inputType).toBe('number');
  });
});
