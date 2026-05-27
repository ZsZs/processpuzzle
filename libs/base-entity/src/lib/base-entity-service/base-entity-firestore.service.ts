import { assertPersistedEntity, BaseEntity, PersistedEntity } from '../base-entity/base-entity';
import { BaseEntityService } from './base-entity.service';
import { from, map, Observable, of } from 'rxjs';
import { BaseEntityLoadResponse, BaseEntityQueryCondition, OrderByDirection } from './base-entity-load-response';
import { Inject, inject } from '@angular/core';
import { collection, deleteDoc, doc, Firestore, getDoc, getDocs, limit, orderBy, query, setDoc, updateDoc, where } from '@angular/fire/firestore';
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
    const savedEntity = { ...entity, id: newDoc.id };
    return new Promise<PersistedEntity<Entity>>((resolve, reject) => {
      resolve(this.mapDocument(savedEntity));
      reject(new Error('Failed to update entity: ' + entity));
    });
  }

  protected createQuery(queryCondition: BaseEntityQueryCondition) {
    let orderBys: QueryOrderByConstraint[] = [];
    if (queryCondition.orderBys && queryCondition.orderBys?.length > 0) {
      orderBys = queryCondition.orderBys?.map((orderByCondition) => {
        const name = Object.keys(OrderByDirection).filter((key) => key === orderByCondition.direction);
        const direction = Object.values(OrderByDirection).filter((value) => value === name[0]);
        return orderBy(orderByCondition.property, direction[0]);
      });
    }

    let wheres: QueryFieldFilterConstraint[] = [];
    if (queryCondition.filters && queryCondition.filters?.length > 0) {
      wheres =
        queryCondition.filters?.map((filter) => {
          return where(filter.property, filter.operator, filter.value);
        }) ?? [];
    }

    return query(this.collection, ...wheres, ...orderBys, limit(queryCondition.pageSize ?? 99));
  }

  protected async findByQueryAsync(queryCondition: BaseEntityQueryCondition): Promise<BaseEntityLoadResponse<PersistedEntity<Entity>> | PersistedEntity<Entity>[]> {
    const query = this.createQuery(queryCondition);
    const results = await getDocs(query);
    const content: PersistedEntity<Entity>[] = [];
    results.docs.forEach((doc) => {
      content.push(this.mapDocument({ id: doc.id, ...doc.data() }));
    });

    return Promise.resolve({
      page: queryCondition.page,
      pageSize: queryCondition.pageSize,
      totalPageCount: 1,
      content,
    });
  }

  protected mapCollection(collection: Observable<any[]>): PersistedEntity<Entity>[] {
    const entities: PersistedEntity<Entity>[] = [];
    collection.pipe(
      map((documents: any[]) => {
        documents.forEach((document) => entities.push(this.mapDocument(document)));
      }),
    );
    return entities;
  }

  protected mapDocument(document: any): PersistedEntity<Entity> {
    const entity = this.entityMapper.fromDto(document);
    assertPersistedEntity(entity);
    return entity;
  }

  private mapEntity(entity: Entity): any {
    return this.entityMapper.toDto(entity);
  }

  private async updateAsync(entity: PersistedEntity<Entity>): Promise<PersistedEntity<Entity>> {
    if (!entity) throw new Error('Entity cant be undefined');

    const docRef = doc(this.firestore, this.collectionName, entity.id);
    await updateDoc(docRef, this.mapEntity(entity));

    return Promise.resolve(entity);
  }

  // endregion
}
