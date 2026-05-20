import type { BaseEntityAttrDescriptor, BaseEntityDescriptor } from '@processpuzzle/base-entity';

// TODO: handle ARTIFACT / COMPONENTS — depends on linked-entity resolution
const DEFERRED_TYPES = new Set<string>(['ARTIFACT', 'COMPONENTS']);

/** Control types whose value identifies another entity (drives form-fill via selection / autocomplete). */
const LINKED_TYPES = new Set<string>(['FOREIGN_KEY', 'LOOKUP']);

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
export function buildCreateData(descriptor: BaseEntityDescriptor, resolvedForeignKeys: Record<string, string> = {}, uniqueSuffix = ''): Record<string, string> {
  const data: Record<string, string> = {};
  const suffix = uniqueSuffix ? ` ${uniqueSuffix}` : '';

  for (const attr of inputAttrs(descriptor)) {
    switch (attr.formControlType as string) {
      case 'TEXT_BOX':
        data[attr.attrName] = `Test ${attr.label ?? attr.attrName}${suffix}`;
        break;
      case 'TEXTAREA':
        data[attr.attrName] = `Description for ${descriptor.entityName}${suffix}`;
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
      // LOOKUP stores the linked entity's `key` field, but we drive selection from the form PO
      // via the linked identification map — leave the data slot empty here.
      case 'LOOKUP':
        data[attr.attrName] = '';
        break;
    }
  }

  return data;
}

/**
 * For each linked-entity attr (FOREIGN_KEY, LOOKUP) on `descriptor`, returns the linked entity's
 * identification value — i.e. the text the autocomplete or readonly input displays.
 * Linked entities must already be in `createdData`.
 */
export function buildLinkedIdentifications(
  descriptor: BaseEntityDescriptor,
  descriptorMap: Map<string, BaseEntityDescriptor>,
  createdData: Record<string, Record<string, string>>,
): Record<string, string> {
  const result: Record<string, string> = {};

  for (const attr of inputAttrs(descriptor)) {
    if (!LINKED_TYPES.has(attr.formControlType as string)) continue;
    const linkedName = attr.linkedEntityType?.entityName;
    if (!linkedName) continue;

    const linkedRow = createdData[linkedName];
    if (!linkedRow) continue;

    if (attr.formControlType === 'LOOKUP') {
      // LOOKUP options render the linked LookupTable row's `value` attribute.
      result[attr.attrName] = linkedRow['value'] ?? '';
      continue;
    }

    // FOREIGN_KEY shows the linked entity's identification attribute.
    const linkedDescriptor = descriptorMap.get(linkedName);
    const linkedIdAttr = linkedDescriptor ? identificationAttr(linkedDescriptor) : undefined;
    if (!linkedIdAttr) continue;

    result[attr.attrName] = linkedRow[linkedIdAttr.attrName] ?? '';
  }

  return result;
}

/** Builds an updated payload */
export function buildUpdateData(descriptor: BaseEntityDescriptor, original: Record<string, string>): Record<string, string> {
  const updated = { ...original };
  for (const attr of inputAttrs(descriptor)) {
    if (attr.isLinkToDetails) {
      continue;
    }

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
