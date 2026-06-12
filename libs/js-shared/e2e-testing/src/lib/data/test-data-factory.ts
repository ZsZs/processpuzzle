import type { BaseEntityAttrDescriptor, BaseEntityDescriptor } from '@processpuzzle/base-entity';
import {
  type ControlDataContext,
  controlTestersFor,
  identificationAttrFromTesters,
  linkedFixtureAttrKey,
} from '../controls/control-tester';

/** Returns only attrs that represent actual form inputs. */
export function inputAttrs(descriptor: BaseEntityDescriptor): BaseEntityAttrDescriptor[] {
  return controlTestersFor(descriptor).map((tester) => tester.attr);
}

/** Returns the identification attr (isLinkToDetails === true). */
export function identificationAttr(descriptor: BaseEntityDescriptor): BaseEntityAttrDescriptor | undefined {
  return identificationAttrFromTesters(descriptor);
}

export function createControlDataContext(
  descriptor: BaseEntityDescriptor,
  descriptorMap: Map<string, BaseEntityDescriptor> = new Map(),
  createdIdsByEntity: Record<string, string> = {},
  createdDataByEntity: Record<string, Record<string, string>> = {},
  uniqueSuffix = '',
): ControlDataContext {
  return {
    descriptor,
    descriptorMap,
    createdIdsByEntity,
    createdDataByEntity,
    uniqueSuffix,
  };
}

/** Builds a valid data payload for CREATE. */
export function buildCreateData(descriptor: BaseEntityDescriptor, resolvedForeignKeys: Record<string, string> = {}, uniqueSuffix = ''): Record<string, string> {
  return buildCreateDataForContext(createControlDataContext(descriptor, new Map(), resolvedForeignKeys, {}, uniqueSuffix));
}

export function buildCreateDataForContext(context: ControlDataContext): Record<string, string> {
  const data: Record<string, string> = {};
  for (const tester of controlTestersFor(context.descriptor)) {
    data[tester.attr.attrName] = tester.createValue(context);
  }
  return data;
}

/**
 * For each linked-entity attr (FOREIGN_KEY, LOOKUP) on `descriptor`, returns the linked entity's
 * identification value, i.e. the text the autocomplete or readonly input displays.
 */
export function buildLinkedIdentifications(
  descriptor: BaseEntityDescriptor,
  descriptorMap: Map<string, BaseEntityDescriptor>,
  createdData: Record<string, Record<string, string>>,
): Record<string, string> {
  const createdIdsByEntity = Object.fromEntries(Object.keys(createdData).map((entityName) => [entityName, '']));
  return buildLinkedIdentificationsForContext({
    descriptor,
    descriptorMap,
    createdDataByEntity: createdData,
    createdIdsByEntity,
  });
}

export function buildLinkedIdentificationsForContext(context: ControlDataContext): Record<string, string> {
  const result: Record<string, string> = {};
  for (const tester of controlTestersFor(context.descriptor)) {
    if (!tester.isLinked) continue;
    const value = tester.createValue(context);
    result[tester.attr.attrName] = tester.displayValue(context, value);
  }
  return result;
}

/** Builds an updated payload. */
export function buildUpdateData(descriptor: BaseEntityDescriptor, original: Record<string, string>): Record<string, string> {
  return buildUpdateDataForContext(createControlDataContext(descriptor), original);
}

export function buildUpdateDataForContext(context: ControlDataContext, original: Record<string, string>): Record<string, string> {
  const updated = { ...original };
  for (const tester of controlTestersFor(context.descriptor)) {
    if (tester.attr.isLinkToDetails) continue;
    updated[tester.attr.attrName] = tester.updateValue(context, original);
  }
  return updated;
}

export { linkedFixtureAttrKey };
