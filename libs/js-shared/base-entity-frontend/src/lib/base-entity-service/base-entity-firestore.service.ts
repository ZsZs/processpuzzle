import { assertPersistedEntity, BaseEntity, PersistedEntity } from '../base-entity/base-entity';
import { BaseEntityService } from './base-entity.service';
import { from, map, Observable, of } from 'rxjs';
import { BaseEntityLoadResponse, BaseEntityQueryCondition, OrderByDirection } from './base-entity-load-response';
import { Inject, inject } from '@angular/core';
import { collection, deleteDoc, doc, DocumentData, Firestore, getDoc, getDocs, limit, orderBy, query, setDoc, updateDoc, where } from '@angular/fire/firestore';
import { BaseEntityMapper } from '../base-entity.mapper';
import { QueryFieldFilterConstraint, QueryOrderByConstraint } from '@firebase/firestore';

export class BaseEntityFirestoreService<Entity extends BaseEntity> implements BaseEntityService<Entity> {
  protected collection;
  protected readonly firestore = inject(Firestore);

  constructor(
    @Inject('entityMapper') protected entityMapper: BaseEntityMapper<Entity>,
    protected collectionName: string,
  ) {
    this.collection = collection(this.firestore, collectionName);
  }

  // region public accessors and mutators
  add(entity: Entity): Observable<PersistedEntity<Entity>> {
    if (!entity) throw new Error('Entity cant be undefined');
    return from(this.addAsync(entity));
  }

  delete(id: string): Observable<unknown> {
    const docRef = doc(this.firestore, this.collectionName, id);
    return from(deleteDoc(docRef));
  }

  deleteAll(): Observable<unknown> {
    return of(undefined);
  }

  findAll(page?: number, pageSize?: number): Observable<BaseEntityLoadResponse<PersistedEntity<Entity>> | PersistedEntity<Entity>[]> {
    return this.findByQuery({ page, pageSize });
  }

  findById(id: string): Observable<PersistedEntity<Entity> | void> {
    const docRef = doc(this.firestore, this.collectionName, id);
    return from(
      getDoc(docRef)
        .then((document) => {
          return document.exists() ? this.mapDocument({ id: document.id, ...document.data() }) : undefined;
        })
        .catch((error) => {
          throw new Error(`Error: ${error} occurred while finding document by id: ${id}`);
        }),
    );
  }

  findByQuery(queryCondition: BaseEntityQueryCondition): Observable<BaseEntityLoadResponse<PersistedEntity<Entity>> | PersistedEntity<Entity>[]> {
    return from(this.findByQueryAsync(queryCondition));
  }

  update(entity: PersistedEntity<Entity>): Observable<PersistedEntity<Entity>> {
    return from(this.updateAsync(entity));
  }

  // endregion

  // protected, private helper methods
  protected async addAsync(entity: Entity): Promise<PersistedEntity<Entity>> {
    const newDoc = entity.id ? doc(this.collection, entity.id) : doc(this.collection);
    await setDoc(newDoc, this.mapEntity(entity));
    return this.mapDocument({ ...entity, id: newDoc.id });
  }

  protected createQuery(queryCondition: BaseEntityQueryCondition) {
    let orderBys: QueryOrderByConstraint[] = [];
    if (queryCondition.orderBys?.length) {
      orderBys = queryCondition.orderBys.map((orderByCondition) => {
        const name = Object.keys(OrderByDirection).find((key) => key === orderByCondition.direction);
        const direction = Object.values(OrderByDirection).find((value) => value === name);
        return orderBy(orderByCondition.property, direction);
      });
    }

    let wheres: QueryFieldFilterConstraint[] = [];
    if (queryCondition.filters?.length) {
      wheres = queryCondition.filters.map((filter) => where(filter.property, filter.operator, filter.value));
    }

    return query(this.collection, ...wheres, ...orderBys, limit(queryCondition.pageSize ?? 99));
  }

  protected async findByQueryAsync(queryCondition: BaseEntityQueryCondition): Promise<BaseEntityLoadResponse<PersistedEntity<Entity>> | PersistedEntity<Entity>[]> {
    const builtQuery = this.createQuery(queryCondition);
    const results = await getDocs(builtQuery);
    const content = results.docs.map((docSnapshot) => this.mapDocument({ id: docSnapshot.id, ...docSnapshot.data() }));

    return {
      number: queryCondition.page,
      size: queryCondition.pageSize,
      totalElements: content.length,
      totalPages: 1,
      content,
    };
  }

  protected mapCollection(source: Observable<unknown[]>): PersistedEntity<Entity>[] {
    const entities: PersistedEntity<Entity>[] = [];
    source.pipe(
      map((documents) => {
        documents.forEach((docData) => entities.push(this.mapDocument(docData)));
      }),
    );
    return entities;
  }

  protected mapDocument(docData: unknown): PersistedEntity<Entity> {
    const entity = this.entityMapper.fromDto(docData);
    assertPersistedEntity(entity);
    return entity;
  }

  private mapEntity(entity: Entity): DocumentData {
    return this.entityMapper.toDto(entity) as DocumentData;
  }

  private async updateAsync(entity: PersistedEntity<Entity>): Promise<PersistedEntity<Entity>> {
    if (!entity) throw new Error('Entity cant be undefined');

    const docRef = doc(this.firestore, this.collectionName, entity.id);
    await updateDoc(docRef, this.mapEntity(entity));

    return entity;
  }

  // endregion
}
