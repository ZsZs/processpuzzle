import { assertPersistedEntity, BaseEntity, PersistedEntity } from '../base-entity/base-entity';
import { BaseEntityService } from './base-entity.service';
import { from, map, Observable, of } from 'rxjs';
import { BaseEntityLoadResponse, BaseEntityQueryCondition, OrderByDirection } from './base-entity-load-response';
import { Inject, inject } from '@angular/core';
import { collection, deleteDoc, doc, DocumentData, Firestore, getDoc, getDocs, limit, orderBy, query, setDoc, updateDoc, where } from '@angular/fire/firestore';
import { BaseEntityMapper } from '../base-entity.mapper';
import { QueryFieldFilterConstraint, QueryOrderByConstraint } from '@firebase/firestore';

/**
 * Talks to Firestore straight from the browser, bypassing the OpenAPI contract entirely.
 *
 * @deprecated Use `BaseEntityRestService` against the contract on every platform. Firebase is
 * now served by Cloud Functions that implement the same yaml the Java backend generates from — see
 * `tools/firebase/functions/src/base-document` for the first of them — so the platform choice is a
 * deployment concern rather than an application one.
 *
 * Why this path is being retired rather than kept as an option:
 * - **It is a second seam.** With an adapter chosen in the client, every feature has to be built
 *   twice, and only the REST half is covered by a contract test. Drift on this side is undetectable
 *   until it reaches a user.
 * - **Its paging is a fiction.** `findByQueryAsync` reports `totalPages: 1` and
 *   `totalElements: content.length`, so a paginator tells the truth on REST and guesses here.
 * - **Firestore cannot answer the query language.** `createQuery` maps a
 *   {@link BaseEntityQueryCondition} onto Firestore constraints, which have no substring or
 *   case-insensitive compare, and silently drop documents that lack the `orderBy` field.
 * - **Authorization ends up in two dialects** — `firestore.rules` here, server-side policy there —
 *   so tenant isolation has to be got right twice.
 *
 * Nothing about it is broken today and no removal date is set: `ApplicationPropertyService` in
 * `widgets` still extends it, and `BaseEntityFacade.createService` still returns it when
 * `BACKEND_SERVICE_PROVIDER` is not `rest`. Those are the call sites to migrate before it can go — the
 * testbed's own Firestore sample is gone, its entities now being served through the contract like every
 * other deployment's.
 */
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
