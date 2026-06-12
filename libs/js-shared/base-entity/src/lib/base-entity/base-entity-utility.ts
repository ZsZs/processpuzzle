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
