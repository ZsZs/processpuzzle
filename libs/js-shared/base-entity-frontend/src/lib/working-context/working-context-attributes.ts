import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { filterAttributeDescriptors } from '../base-entity/filter-attr-descriptor';

/** The placeholder name that stands for the entity's STATE attribute when it has no attribute of that name. */
export const STATE_ALIAS = 'state';

const PLACEHOLDER = /\{\{\s*([\w.]+)\s*\}\}/g;

/**
 * The attribute a context declaration means by `key`. Usually `key` itself; `state` falls back to the
 * descriptor's STATE attribute, so a declaration can say "the state" without knowing what the entity calls it.
 */
export function resolveAttrName(key: string, descriptor: BaseEntityDescriptor | undefined, stateAttr?: string): string {
  if (key !== STATE_ALIAS) return key;
  // The state machine's own word for it beats a guess from the descriptor, which only knows once its screens were resolved.
  if (stateAttr) return stateAttr;
  if (!descriptor) return key;
  const attrs = filterAttributeDescriptors(descriptor.attrDescriptors);
  if (attrs.some((attr) => attr.attrName === key)) return key;
  return attrs.find((attr) => attr.formControlType === FormControlType.STATE)?.attrName ?? key;
}

/** The value at a dotted path — `currentRound.number` — or `undefined` when any step is missing. */
export function valueAt(entity: unknown, path: string, descriptor?: BaseEntityDescriptor, stateAttr?: string): unknown {
  const [head, ...rest] = path.split('.');
  let value: unknown = (entity as Record<string, unknown> | undefined)?.[resolveAttrName(head, descriptor, stateAttr)];
  for (const step of rest) value = value != null && typeof value === 'object' ? (value as Record<string, unknown>)[step] : undefined;
  return value;
}

/** Fills `{{attr}}` placeholders from the entity; a missing value renders as nothing. */
export function renderTemplate(template: string, entity: unknown, descriptor?: BaseEntityDescriptor, stateAttr?: string): string {
  return template
    .replace(PLACEHOLDER, (_match, path: string) => {
      const value = valueAt(entity, path, descriptor, stateAttr);
      return value == null ? '' : String(value);
    })
    .trim();
}

/** Whether every condition holds on the entity. An array value is a list of alternatives. */
export function matchesConditions(entity: unknown, conditions: Record<string, unknown> | undefined, descriptor?: BaseEntityDescriptor, stateAttr?: string): boolean {
  if (!entity || !conditions) return false;
  const entries = Object.entries(conditions);
  if (entries.length === 0) return false;
  return entries.every(([key, expected]) => {
    const actual = valueAt(entity, key, descriptor, stateAttr);
    return Array.isArray(expected) ? expected.includes(actual) : actual === expected;
  });
}

/** What a context's entity is called on screen: the `display.label` template, else its title attribute, else its id. */
export function contextLabel(labelTemplate: string | undefined, entity: Record<string, unknown> | undefined, descriptor: BaseEntityDescriptor | undefined, id: string): string {
  if (!entity) return id;
  const label = labelTemplate ? renderTemplate(labelTemplate, entity, descriptor) : String(entity[descriptor?.titleAttrName() ?? 'id'] ?? '');
  return label.length > 0 ? label : id;
}
