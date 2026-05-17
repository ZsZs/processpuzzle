import type { BaseEntityAttrDescriptor, BaseEntityDescriptor } from '@processpuzzle/base-entity';

/** Topological sort — entities with no FK deps come first */
export function resolveDependencyOrder(entities: BaseEntityDescriptor[]): BaseEntityDescriptor[] {
  const nameMap = new Map(entities.map((e) => [e.entityName, e]));
  const visited = new Set<string>();
  const result: BaseEntityDescriptor[] = [];

  function visit(entity: BaseEntityDescriptor) {
    if (visited.has(entity.entityName)) return;
    visited.add(entity.entityName);

    for (const attr of entity.attrDescriptors as BaseEntityAttrDescriptor[]) {
      if ((attr.formControlType as string) === 'FOREIGN_KEY' && attr.linkedEntityType) {
        const dep = nameMap.get(attr.linkedEntityType.entityName);
        if (dep) visit(dep);
      }
    }

    result.push(entity);
  }

  for (const entity of entities) visit(entity);
  return result;
}
