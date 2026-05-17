import type { BaseEntityAttrDescriptor, BaseEntityDescriptor } from '@processpuzzle/base-entity';

// TODO: handle ARTIFACT / LOOKUP / COMPONENTS — depends on linked-entity resolution
const DEFERRED_TYPES = new Set<string>(['ARTIFACT', 'LOOKUP', 'COMPONENTS']);

/** Layout containers and non-input types — never fill these */
const SKIP_TYPES = new Set<string>(['FLEX_BOX', ...DEFERRED_TYPES]);

const INITIAL_DATE = '2026-01-15';
const UPDATED_DATE = '2026-02-20';

/** Returns only attrs that represent actual form inputs */
export function inputAttrs(descriptor: BaseEntityDescriptor): BaseEntityAttrDescriptor[] {
  return (descriptor.attrDescriptors as BaseEntityAttrDescriptor[]).filter(
    (attr) => !SKIP_TYPES.has(attr.formControlType) && attr.visible !== false,
  );
}

/** Returns the identification attr (isLinkToDetails === true) */
export function identificationAttr(descriptor: BaseEntityDescriptor): BaseEntityAttrDescriptor | undefined {
  return inputAttrs(descriptor).find((attr) => attr.isLinkToDetails === true);
}

/** Builds a valid data payload for CREATE */
export function buildCreateData(descriptor: BaseEntityDescriptor, resolvedForeignKeys: Record<string, string> = {}): Record<string, string> {
  const data: Record<string, string> = {};

  for (const attr of inputAttrs(descriptor)) {
    switch (attr.formControlType as string) {
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
        data[attr.attrName] = String(attr.selectables?.[0]?.value ?? '');
        break;
      case 'TAGS':
        data[attr.attrName] = 'alpha,beta';
        break;
      case 'FOREIGN_KEY': {
        const linked = attr.linkedEntityType?.entityName;
        data[attr.attrName] = linked ? (resolvedForeignKeys[linked] ?? '') : '';
        break;
      }
    }
  }

  return data;
}

/** Builds an updated payload */
export function buildUpdateData(descriptor: BaseEntityDescriptor, original: Record<string, string>): Record<string, string> {
  const updated = { ...original };
  for (const attr of inputAttrs(descriptor)) {
    switch (attr.formControlType as string) {
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
        updated[attr.attrName] = String(attr.selectables?.[1]?.value ?? original[attr.attrName] ?? '');
        break;
      case 'TAGS':
        updated[attr.attrName] = `${original[attr.attrName] ?? ''},gamma`;
        break;
    }
  }
  return updated;
}
