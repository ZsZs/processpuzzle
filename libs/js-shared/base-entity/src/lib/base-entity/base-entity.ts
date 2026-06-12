export interface BaseEntity {
  readonly id?: string;
}

export interface PersistedBaseEntity extends BaseEntity {
  readonly id: string;
}

export type PersistedEntity<Entity extends BaseEntity> = Entity & PersistedBaseEntity;

export function assertPersistedEntity<Entity extends BaseEntity>(entity: Entity): asserts entity is PersistedEntity<Entity> {
  if (typeof entity.id !== 'string') throw new Error('Persisted entity must have an id');
}
