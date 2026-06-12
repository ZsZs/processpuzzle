import { BaseEntity, PersistedEntity } from '../base-entity/base-entity';
import { BaseEntityLoadResponse, BaseEntityQueryCondition } from './base-entity-load-response';
import { Observable } from 'rxjs';
import { InjectionToken } from '@angular/core';

export const BASE_ENTITY_SERVICE = new InjectionToken<BaseEntityService<any>>('BASE_ENTITY_SERVICE');

export interface BaseEntityService<Entity extends BaseEntity> {
  add(entity: Entity, id?: number): Observable<PersistedEntity<Entity>>;

  delete(id: string): Observable<unknown>;

  deleteAll(): Observable<unknown>;

  findAll(page?: number, pageSize?: number): Observable<BaseEntityLoadResponse<PersistedEntity<Entity>> | PersistedEntity<Entity>[] | PersistedEntity<Entity>>;

  findById(id: string): Observable<PersistedEntity<Entity> | void>;

  findByQuery(condition: BaseEntityQueryCondition): Observable<BaseEntityLoadResponse<PersistedEntity<Entity>> | PersistedEntity<Entity>[] | PersistedEntity<Entity>>;

  update(entity: PersistedEntity<Entity>): Observable<PersistedEntity<Entity>>;
}
