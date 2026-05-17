// src/support/dependency-resolver.ts
import { EntityDescriptor } from '../types/entity-descriptor.types';

/** Topological sort — entities with no FK deps come first */
export function resolveDependencyOrder(entities: EntityDescriptor[]): EntityDescriptor[] {
  const nameMap = new Map(entities.map((e) => [e.entityName, e]));
  const visited = new Set<string>();
  const result: EntityDescriptor[] = [];

  function visit(entity: EntityDescriptor) {
    if (visited.has(entity.entityName)) return;
    visited.add(entity.entityName);

    // visit FK dependencies first
    for (const attr of entity.attrDescriptors) {
      if (attr.formControlType === 'FOREIGN_KEY' && attr.linkedEntityType) {
        const dep = nameMap.get(attr.linkedEntityType.entityName);
        if (dep) visit(dep);
      }
    }

    result.push(entity);
  }

  for (const entity of entities) visit(entity);
  return result;
}
