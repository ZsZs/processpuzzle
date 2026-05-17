// src/support/test-data-factory.ts
import { AttrDescriptor, EntityDescriptor } from '../types/entity-descriptor.types';

// TODO: handle ARTIFACT / LOOKUP / COMPONENTS — depends on linked-entity resolution
const DEFERRED_TYPES = new Set(['ARTIFACT', 'LOOKUP', 'COMPONENTS']);

/** Layout containers and non-input types — never fill these */
const SKIP_TYPES = new Set(['FLEX_BOX', ...DEFERRED_TYPES]);

const INITIAL_DATE = '2026-01-15';
const UPDATED_DATE = '2026-02-20';

/** Returns only attrs that represent actual form inputs */
export function inputAttrs(descriptor: EntityDescriptor): AttrDescriptor[] {
  return descriptor.attrDescriptors.filter((attr) => !SKIP_TYPES.has(attr.formControlType) && attr.visible !== false);
}

/** Returns the identification attr (isLinkToDetails === true) */
export function identificationAttr(descriptor: EntityDescriptor): AttrDescriptor | undefined {
  return inputAttrs(descriptor).find((attr) => attr.isLinkToDetails === true);
}

/** Builds a valid data payload for CREATE */
export function buildCreateData(descriptor: EntityDescriptor, resolvedForeignKeys: Record<string, string> = {}): Record<string, string> {
  const data: Record<string, string> = {};

  for (const attr of inputAttrs(descriptor)) {
    switch (attr.formControlType) {
      case 'TEXT_BOX':
        data[attr.attrName] = `Test ${attr.label ?? attr.attrName}`;
        break;
      case 'TEXTAREA':
        data[attr.attrName] = `Description for ${descriptor.entityName}`;
        break;
      case 'CHECKBOX':
        data[attr.attrName] = 'true';
        break;
      case 'DATE':
        data[attr.attrName] = INITIAL_DATE;
        break;
      case 'DROPDOWN':
        data[attr.attrName] = attr.selectables?.[0]?.value ?? '';
        break;
      case 'TAGS':
        data[attr.attrName] = 'alpha,beta';
        break;
      case 'FOREIGN_KEY': // resolved from a previously created linked entity
      {
        const linked = attr.linkedEntityType?.entityName;
        data[attr.attrName] = linked ? (resolvedForeignKeys[linked] ?? '') : '';
        break;
      }
    }
  }

  return data;
}

/** Builds an updated payload */
export function buildUpdateData(descriptor: EntityDescriptor, original: Record<string, string>): Record<string, string> {
  const updated = { ...original };
  for (const attr of inputAttrs(descriptor)) {
    switch (attr.formControlType) {
      case 'TEXT_BOX':
      case 'TEXTAREA':
        updated[attr.attrName] = `Updated ${original[attr.attrName] ?? attr.attrName}`;
        break;
      case 'CHECKBOX':
        updated[attr.attrName] = original[attr.attrName] === 'true' ? 'false' : 'true';
        break;
      case 'DATE':
        updated[attr.attrName] = UPDATED_DATE;
        break;
      case 'DROPDOWN':
        updated[attr.attrName] = attr.selectables?.[1]?.value ?? original[attr.attrName] ?? '';
        break;
      case 'TAGS':
        updated[attr.attrName] = `${original[attr.attrName] ?? ''},gamma`;
        break;
    }
  }
  return updated;
}
