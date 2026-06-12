import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { controlTestersFor } from '../controls/control-tester';

/** Topological sort — entities with no linked-entity deps come first */
export function resolveDependencyOrder(entities: BaseEntityDescriptor[]): BaseEntityDescriptor[] {
  const nameMap = new Map(entities.map((e) => [e.entityName, e]));
  const visited = new Set<string>();
  const result: BaseEntityDescriptor[] = [];

  function visit(entity: BaseEntityDescriptor) {
    if (visited.has(entity.entityName)) return;
    visited.add(entity.entityName);

    for (const tester of controlTestersFor(entity)) {
      const linkedName = tester.linkedEntityName();
      if (tester.isLinked && linkedName) {
        const dep = nameMap.get(linkedName);
        if (dep) visit(dep);
      }
    }

    result.push(entity);
  }

  for (const entity of entities) visit(entity);
  return result;
}
