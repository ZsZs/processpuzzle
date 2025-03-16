import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityService } from './base-entity.service';
import { from, map, Observable, of } from 'rxjs';
import { BaseEntityLoadResponse, BaseEntityQueryCondition, OrderByDirection } from './base-entity-load-response';
import { Inject, inject } from '@angular/core';
import { collection, deleteDoc, doc, Firestore, getDoc, getDocs, limit, orderBy, query, setDoc, updateDoc, where } from '@angular/fire/firestore';
import { BaseEntityMapper } from '../base-entity.mapper';
import { QueryFieldFilterConstraint, QueryOrderByConstraint } from '@firebase/firestore';

export abstract class BaseEntityFirestoreService<Entity extends BaseEntity> implements BaseEntityService<Entity> {
  protected collection;
  protected readonly firestore = inject(Firestore);

  protected constructor(
    @Inject('entityMapper') protected entityMapper: BaseEntityMapper<Entity>,
    protected collectionName: string,
  ) {
    this.collection = collection(this.firestore, collectionName);
  }

  // region public accessors and mutators
  add(entity: Entity): Observable<Entity> {
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

  findAll(page?: number, pageSize?: number): Observable<BaseEntityLoadResponse<Entity> | Entity[]> {
    return this.findByQuery({ page, pageSize });
  }

  findById(id: string): Observable<Entity | void> {
    const docRef = doc(this.firestore, this.collectionName, id);
    return from(
      getDoc(docRef)
        .then((document) => {
          return this.mapDocument(document.data() as Entity);
        })
        .catch((error) => {
          throw new Error(`Error: ${error} occurred while finding document by id: ${id}`);
        }),
    );
  }

  findByQuery(queryCondition: BaseEntityQueryCondition): Observable<BaseEntityLoadResponse<Entity> | Entity[]> {
    return from(this.findByQueryAsync(queryCondition));
  }

  update(entity: Entity): Observable<Entity> {
    return from(this.updateAsync(entity));
  }

  // endregion

  // protected, private helper methods
  protected async addAsync(entity: Entity): Promise<Entity> {
    const newDoc = doc(this.collection, entity.id);
    await setDoc(newDoc, this.mapEntity(entity));
    return new Promise<Entity>((resolve, reject) => {
      resolve(entity);
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

  protected async findByQueryAsync(queryCondition: BaseEntityQueryCondition): Promise<BaseEntityLoadResponse<Entity> | Entity[]> {
    const query = this.createQuery(queryCondition);
    const results = await getDocs(query);
    const content: Entity[] = [];
    results.docs.forEach((doc) => {
      content.push(this.mapDocument({ id: doc.id, ...doc.data() } as Entity));
    });

    return Promise.resolve({
      page: queryCondition.page,
      pageSize: queryCondition.pageSize,
      totalPageCount: 1,
      content,
    });
  }

  protected mapCollection(collection: Observable<any[]>): Entity[] {
    const entities: Entity[] = [];
    collection.pipe(
      map((documents: any[]) => {
        documents.forEach((document) => entities.push(this.entityMapper.fromDto(document)));
      }),
    );
    return entities;
  }

  protected mapDocument(document: any): Entity {
    return this.entityMapper.fromDto(document);
  }

  private mapEntity(entity: Entity): any {
    return this.entityMapper.toDto(entity);
  }

  private async updateAsync(entity: Entity): Promise<Entity> {
    if (!entity) throw new Error('Entity cant be undefined');

    const docRef = doc(this.firestore, this.collectionName, entity.id);
    await updateDoc(docRef, this.mapEntity(entity));

    return Promise.resolve(entity);
  }

  // endregion
}
