import { BaseEntityAttrDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { describe, expect, it } from 'vitest';
import { hasDescribedProps, propsSchemaToDescriptors } from './props-schema-to-descriptors';
import { PropsSchema } from './widget-definition';

/**
 * The question this whole resource exists to answer: is `propsSchema` expressive enough to drive a
 * real props form? The two schemas below are the props of widgets that actually exist —
 * `entity-grid` as documented in base-app-api.yaml, and `cards-grid` — so the answer is measured
 * against real cases rather than a schema written to suit the mapper.
 */
describe('propsSchemaToDescriptors', () => {
  const descriptorFor = (schema: PropsSchema, name: string) => propsSchemaToDescriptors(schema).find((d) => d.attrName === name) as BaseEntityAttrDescriptor;

  describe('the entity-grid props documented in base-app-api.yaml', () => {
    const ENTITY_GRID: PropsSchema = {
      type: 'object',
      required: ['entityName'],
      properties: {
        entityName: { type: 'string', title: 'Entity', description: 'BaseEntityDescriptor.entityName' },
        columns: { type: 'array', items: { type: 'string' }, title: 'Columns' },
        rsqlFilter: { type: 'string', title: 'Filter' },
        pageSize: { type: 'integer', title: 'Page size', default: 20 },
        dense: { type: 'boolean', title: 'Dense' },
      },
    };

    it('produces one descriptor per declared prop, in declaration order', () => {
      expect(propsSchemaToDescriptors(ENTITY_GRID).map((d) => d.attrName)).toEqual(['entityName', 'columns', 'rsqlFilter', 'pageSize', 'dense']);
    });

    it('maps each JSON Schema type onto the matching control', () => {
      expect(descriptorFor(ENTITY_GRID, 'entityName').formControlType).toBe(FormControlType.TEXT_BOX);
      expect(descriptorFor(ENTITY_GRID, 'columns').formControlType).toBe(FormControlType.TAGS);
      expect(descriptorFor(ENTITY_GRID, 'pageSize').formControlType).toBe(FormControlType.TEXT_BOX);
      expect(descriptorFor(ENTITY_GRID, 'dense').formControlType).toBe(FormControlType.CHECKBOX);
    });

    it('carries required through from the schema', () => {
      expect(descriptorFor(ENTITY_GRID, 'entityName').required).toBe(true);
      expect(descriptorFor(ENTITY_GRID, 'rsqlFilter').required).toBe(false);
    });

    it('uses title as the label and description as the placeholder', () => {
      const entityName = descriptorFor(ENTITY_GRID, 'entityName');
      expect(entityName.label).toBe('Entity');
      expect(entityName.placeholder).toBe('BaseEntityDescriptor.entityName');
    });

    it('falls back to the default value as a hint when there is no description', () => {
      expect(descriptorFor(ENTITY_GRID, 'pageSize').placeholder).toBe('Default: 20');
    });

    /** Without this the rendered input is a text field and the browser offers no numeric affordance. */
    it('marks numeric props as numeric inputs', () => {
      expect(descriptorFor(ENTITY_GRID, 'pageSize').options).toMatchObject({ inputType: 'number' });
      expect(descriptorFor(ENTITY_GRID, 'entityName').options).toMatchObject({ inputType: 'text' });
    });
  });

  describe('enums and formats', () => {
    const CARDS_GRID: PropsSchema = {
      type: 'object',
      properties: {
        variant: { type: 'string', enum: ['flat', 'raised', 'outlined'] },
        publishedAfter: { type: 'string', format: 'date' },
        blurb: { type: 'string', maxLength: 4000 },
        heading: { type: 'string', maxLength: 80 },
      },
    };

    it('renders an enum as a dropdown carrying its options', () => {
      const variant = descriptorFor(CARDS_GRID, 'variant');
      expect(variant.formControlType).toBe(FormControlType.DROPDOWN);
      expect(variant.getSelectables()).toEqual([
        { key: 'flat', value: 'flat' },
        { key: 'raised', value: 'raised' },
        { key: 'outlined', value: 'outlined' },
      ]);
    });

    it('renders a date-formatted string as a date control', () => {
      expect(descriptorFor(CARDS_GRID, 'publishedAfter').formControlType).toBe(FormControlType.DATE);
    });

    it('renders a long string as a textarea and a short one as a text box', () => {
      expect(descriptorFor(CARDS_GRID, 'blurb').formControlType).toBe(FormControlType.TEXTAREA);
      expect(descriptorFor(CARDS_GRID, 'heading').formControlType).toBe(FormControlType.TEXT_BOX);
    });

    it('falls back to the attribute name when the schema gives no title', () => {
      expect(descriptorFor(CARDS_GRID, 'variant').label).toBe('variant');
    });
  });

  /**
   * The behaviour that makes widening the supported subset safe: anything unreadable still yields a
   * usable control. If one of these ever throws or drops the prop, an unrecognised keyword has
   * become a data-loss bug.
   */
  describe('unsupported schema constructs fall back rather than fail', () => {
    const EXOTIC: PropsSchema = {
      type: 'object',
      properties: {
        nested: { type: 'object', properties: { deep: { type: 'string' } } },
        rows: { type: 'array', items: { type: 'object' } },
        either: { oneOf: [{ type: 'string' }, { type: 'number' }] },
        untyped: { title: 'No type at all' },
      },
    };

    it('keeps every prop, using the open editor for the ones it cannot read', () => {
      const descriptors = propsSchemaToDescriptors(EXOTIC);
      expect(descriptors.map((d) => d.attrName)).toEqual(['nested', 'rows', 'either', 'untyped']);
      descriptors.forEach((d) => expect(d.formControlType).toBe(FormControlType.ADDITIONAL_PROPERTIES));
    });
  });

  describe('absent and empty schemas', () => {
    it('yields no descriptors when there is no schema', () => {
      expect(propsSchemaToDescriptors(undefined)).toEqual([]);
      expect(propsSchemaToDescriptors(null)).toEqual([]);
      expect(propsSchemaToDescriptors({ type: 'object' })).toEqual([]);
    });

    /**
     * "No schema — props unconstrained" and "a schema declaring no props" are different states in
     * the contract, and the form has to tell them apart: the first shows the open editor, the second
     * shows nothing.
     */
    it('distinguishes an absent schema from one that declares no props', () => {
      expect(hasDescribedProps(undefined)).toBe(false);
      expect(hasDescribedProps({ type: 'object', properties: {} })).toBe(false);
      expect(hasDescribedProps({ type: 'object', properties: { a: { type: 'string' } } })).toBe(true);
    });
  });
});
