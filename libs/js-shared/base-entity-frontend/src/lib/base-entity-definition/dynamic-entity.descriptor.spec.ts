import { describe, expect, it } from 'vitest';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { filterAttributeDescriptors } from '../base-entity/filter-attr-descriptor';
import { controlTypeOf, descriptorOf, referenceIdFieldOf } from './dynamic-entity.descriptor';
import { EntityDefinition } from './entity-definition';
import { definitionLookup, ORDER_DEFINITION, ORDER_LINE_DEFINITION } from './test-entity-definition';

const lookup = definitionLookup();

function attrOf(definition: EntityDefinition, attrName: string) {
  return filterAttributeDescriptors(descriptorOf(definition, lookup).attrDescriptors).find((attrDescriptor) => attrDescriptor.attrName === attrName);
}

describe('descriptorOf', () => {
  it('names the entity by the definition name, not by its code — the descriptor is what URLs and registries are keyed by', () => {
    const descriptor = descriptorOf(ORDER_DEFINITION, lookup);

    expect(descriptor.entityName).toBe('Order');
    expect(descriptor.scopeRoot()).toBe('order');
    expect(descriptor.i18nKey()).toBe('order._self');
  });

  it('orders the attributes by displayOrder, which is the form and table order', () => {
    const attrNames = descriptorOf(ORDER_DEFINITION, lookup).attrDescriptors.map((attrDescriptor) => attrDescriptor.attrName);

    expect(attrNames).toEqual(['orderNumber', 'customerName', 'status', 'total', 'shippingAddress', 'lineItems']);
  });

  it('keeps the arrival order of attributes that declare no displayOrder, so an unordered definition still renders as authored', () => {
    const definition: EntityDefinition = {
      code: 'unordered',
      name: 'Unordered',
      attributes: [
        { code: 'second', formControlType: 'TEXT' },
        { code: 'first', displayOrder: 1, formControlType: 'TEXT' },
        { code: 'third', formControlType: 'TEXT' },
      ],
    };

    expect(descriptorOf(definition, lookup).attrDescriptors.map((attrDescriptor) => attrDescriptor.attrName)).toEqual(['first', 'second', 'third']);
  });

  it('labels an attribute by its authored name, falling back to the code', () => {
    expect(attrOf(ORDER_DEFINITION, 'orderNumber')?.label).toBe('Order #');
    expect(attrOf({ code: 'x', name: 'X', attributes: [{ code: 'unnamed', formControlType: 'TEXT' }] }, 'unnamed')?.label).toBe('unnamed');
  });

  it('carries required through, so the generated form validates what the backend would reject', () => {
    expect(attrOf(ORDER_DEFINITION, 'orderNumber')?.required).toBe(true);
    expect(attrOf(ORDER_DEFINITION, 'total')?.required).toBe(false);
  });

  it('marks the isLinkToDetails attribute, which is what the list links from and the status bar names', () => {
    const descriptor = descriptorOf(ORDER_DEFINITION, lookup);

    expect(descriptor.componentIdentification()).toBe('orderNumber');
    expect(descriptor.titleAttrName()).toBe('orderNumber');
  });

  it('turns enumValues into the dropdown options', () => {
    expect(attrOf(ORDER_DEFINITION, 'status')?.getSelectables()).toEqual([
      { key: 'DRAFT', value: 'DRAFT' },
      { key: 'CONFIRMED', value: 'CONFIRMED' },
      { key: 'SHIPPED', value: 'SHIPPED' },
      { key: 'DELIVERED', value: 'DELIVERED' },
      { key: 'CANCELLED', value: 'CANCELLED' },
    ]);
  });

  it('leaves selectables undefined for an attribute with no enumValues, rather than an empty option list', () => {
    expect(attrOf(ORDER_DEFINITION, 'customerName')?.getSelectables()).toBeUndefined();
  });

  // The contract names its links by definition code; every descriptor API — embeddedAttrFor,
  // isComponentOf, the navigator — compares entity names.
  it('translates a linked definition code into the linked entity name', () => {
    expect(attrOf(ORDER_DEFINITION, 'lineItems')?.linkedEntityType).toBe('Order Line');
    expect(descriptorOf(ORDER_DEFINITION, lookup).embeddedAttrFor('Order Line')?.attrName).toBe('lineItems');
  });

  it('leaves a link to a definition nothing answers to unresolved rather than passing the raw code on', () => {
    const definition: EntityDefinition = { code: 'x', name: 'X', attributes: [{ code: 'ref', formControlType: 'EMBEDDED_COMPONENTS', linkedEntityType: 'deleted' }] };

    expect(attrOf(definition, 'ref')?.linkedEntityType).toBeUndefined();
  });

  it('hides an embedded list from the table — a list of sub-forms is not a cell', () => {
    expect(attrOf(ORDER_DEFINITION, 'lineItems')?.hideInTable).toBe(true);
    expect(attrOf(ORDER_DEFINITION, 'customerName')?.hideInTable).toBe(false);
  });

  it('declares the embedded parents by name, so the child knows which aggregate may carry it', () => {
    const descriptor = descriptorOf(ORDER_LINE_DEFINITION, lookup);

    expect(descriptor.isEmbedded).toBe(true);
    expect(descriptor.componentParents).toEqual(['Order']);
    expect(descriptor.isComponentOf('Order')).toBe(true);
    // An embedded component is located by its position in the payload, never by a foreign key.
    expect(descriptor.parentReferenceAttrName()).toBeUndefined();
  });

  /**
   * `BaseEntityDescriptor` throws on `isEmbedded` with no parent. A definition naming a parent this tenant
   * has since deleted would otherwise take the whole route down while the router is building children.
   */
  it('drops isEmbedded when no parent name resolves, rather than throwing while routes are built', () => {
    const orphan: EntityDefinition = { code: 'orphan', name: 'Orphan', isEmbedded: true, componentParents: ['gone'], attributes: [] };

    expect(() => descriptorOf(orphan, lookup)).not.toThrow();
    expect(descriptorOf(orphan, lookup).isEmbedded).toBe(false);
  });
});

describe('controlTypeOf', () => {
  // The contract's enum is a superset of the frontend's: it names value kinds where the frontend names the
  // widget it renders them with. Every value on the right has to be a key of FORM_CONTROL_COMPONENTS.
  it.each([
    ['TEXT', FormControlType.TEXT_BOX],
    ['TEXT_BOX', FormControlType.TEXT_BOX],
    ['NUMBER', FormControlType.TEXT_BOX],
    ['TEXTAREA', FormControlType.TEXTAREA],
    ['BOOLEAN', FormControlType.CHECKBOX],
    ['CHECKBOX', FormControlType.CHECKBOX],
    ['DATE', FormControlType.DATE],
    ['DATE_TIME', FormControlType.DATE],
    ['ENUM_SELECT', FormControlType.DROPDOWN],
    ['DROPDOWN', FormControlType.DROPDOWN],
    ['RADIO', FormControlType.RADIO],
    ['TAGS', FormControlType.TAGS],
    ['ARTIFACT', FormControlType.ARTIFACT],
    ['FOREIGN_KEY', FormControlType.FOREIGN_KEY],
    ['EMBEDDED_COMPONENTS', FormControlType.EMBEDDED_COMPONENTS],
    ['COMPONENTS', FormControlType.COMPONENTS],
    ['RELATED_ENTITIES', FormControlType.RELATED_ENTITIES],
    ['ADDITIONAL_PROPERTIES', FormControlType.ADDITIONAL_PROPERTIES],
    ['LOOKUP', FormControlType.LOOKUP],
    ['LABEL', FormControlType.LABEL],
    ['TITLE', FormControlType.TITLE],
    ['FLEX_BOX', FormControlType.FLEX_BOX],
  ])('maps the contract %s onto %s', (formControlType, expected) => {
    expect(controlTypeOf({ code: 'attr', formControlType })).toBe(expected);
  });

  // A definition may name a control this frontend has not caught up to. Degrading at the one field beats
  // 'Undefined form control type' from BaseEntityFormBuilder, which takes the whole form down.
  it('falls back to a text box for a control type it does not know', () => {
    expect(controlTypeOf({ code: 'attr', formControlType: 'SIGNATURE_PAD' })).toBe(FormControlType.TEXT_BOX);
  });

  it('gives a numeric attribute a numeric input, by control type or by value kind', () => {
    expect(attrOf(ORDER_LINE_DEFINITION, 'quantity')?.options.inputType).toBe('number');
    expect(attrOf(ORDER_LINE_DEFINITION, 'productName')?.options.inputType).toBe('text');
  });
});

describe('referenceIdFieldOf', () => {
  /**
   * The one that is not cosmetic: with no referenceIdField, `rowId()` reads a non-existent `id`, returns ''
   * and `indexOfRow` answers -1 for every row — so no embedded form can be opened at all.
   */
  it('identifies an embedded row by the child definition title attribute', () => {
    expect(referenceIdFieldOf({ code: 'lineItems', formControlType: 'EMBEDDED_COMPONENTS', linkedEntityType: 'order-line' }, lookup)).toBe('productName');
    expect(attrOf(ORDER_DEFINITION, 'lineItems')?.referenceIdField).toBe('productName');
  });

  /**
   * The whole failure mode above, reachable by forgetting one checkbox while authoring — so an embedded
   * child that names no title attribute is keyed by its leading field instead of by an `id` it cannot have.
   */
  it('keys an embedded child that declares no title attribute by its leading attribute', () => {
    const child: EntityDefinition = {
      code: 'keyed',
      name: 'Keyed',
      isEmbedded: true,
      componentParents: ['order'],
      attributes: [
        { code: 'note', formControlType: 'TEXTAREA', displayOrder: 2 },
        { code: 'label', formControlType: 'TEXT', displayOrder: 1 },
      ],
    };

    expect(referenceIdFieldOf({ code: 'rows', formControlType: 'EMBEDDED_COMPONENTS', linkedEntityType: 'keyed' }, definitionLookup([child]))).toBe('label');
  });

  it('falls back to id for a child that is not embedded, keeping the default behaviour for rows that carry ids', () => {
    const child: EntityDefinition = { code: 'keyed', name: 'Keyed', attributes: [{ code: 'label', formControlType: 'TEXT' }] };

    expect(referenceIdFieldOf({ code: 'rows', formControlType: 'EMBEDDED_COMPONENTS', linkedEntityType: 'keyed' }, definitionLookup([child]))).toBe('id');
  });

  it('falls back to id for an embedded child with no attributes at all', () => {
    const child: EntityDefinition = { code: 'keyed', name: 'Keyed', isEmbedded: true, componentParents: ['order'] };

    expect(referenceIdFieldOf({ code: 'rows', formControlType: 'EMBEDDED_COMPONENTS', linkedEntityType: 'keyed' }, definitionLookup([child]))).toBe('id');
  });

  it('falls back to id when the attribute names no child at all', () => {
    expect(referenceIdFieldOf({ code: 'rows', formControlType: 'EMBEDDED_COMPONENTS' }, lookup)).toBe('id');
  });
});
