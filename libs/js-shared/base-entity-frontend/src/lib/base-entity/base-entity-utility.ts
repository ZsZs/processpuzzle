import { BaseEntity } from './base-entity';

export function entityNameFromType<Entity extends BaseEntity>(entityType: new () => Entity): string {
  return (entityType as { name?: string }).name ?? 'base-entity';
}

export function createTestId<Entity extends BaseEntity>(entity: string | (new () => Entity), suffix: string) {
  const entityName = typeof entity === 'string' ? entity : entityNameFromType(entity);
  const camelCaseName = toTestId(entityName);
  return `${camelCaseName}-${suffix}`;
}

export function createTestLocator<Entity extends BaseEntity>(entity: string | (new () => Entity), suffix: string): string {
  const entityName = typeof entity === 'string' ? entity : entityNameFromType(entity);
  const camelCaseName = toTestId(entityName);
  return `[data-testid="${camelCaseName}-${suffix}"]`;
}

export function toTestId(entityName: string): string {
  return entityName
    .split(' ')
    .map((word, i) => (i === 0 ? word.charAt(0).toLowerCase() + word.slice(1) : word.charAt(0).toUpperCase() + word.slice(1)))
    .join('');
}

/**
 * Underscore snake-case of an entity name, used as the transloco key root for the entity and its
 * attributes: `"Base Entity" -> "base_entity"`, `"IT Variant" -> "it_variant"`, `"Order" -> "order"`.
 */
export function toI18nKey(entityName: string): string {
  return entityName
    .replace(/\s+/g, '') // strip whitespace
    .replace(/([A-Z]+)(?=[A-Z][a-z])/g, '$1_') // "ITVariant" -> "IT_Variant"
    .replace(/([a-z\d])([A-Z])/g, '$1_$2') // "OrderLine" -> "Order_Line"
    .toLowerCase();
}
