import { assertPersistedEntity, BaseEntity, PersistedEntity } from '../base-entity/base-entity';
import { BaseEntityService } from './base-entity.service';
import { from, map, Observable, of } from 'rxjs';
import { BaseEntityLoadResponse, BaseEntityQueryCondition, OrderByDirection } from './base-entity-load-response';
import { Inject, inject } from '@angular/core';
import type { DocumentData } from '@angular/fire/firestore';
import { BaseEntityMapper } from '../base-entity.mapper';
import { QueryFieldFilterConstraint, QueryOrderByConstraint } from '@firebase/firestore';
import { FIRESTORE } from './firestore.token';
// Firestore's free functions come through DI rather than a module import, so a test can substitute them
// with TestBed. See firestore-api.ts for why importing them here cannot be tested reliably.
import { FIRESTORE_API } from './firestore-api';

/**
 * Talks to Firestore straight from the browser, bypassing the OpenAPI contract entirely.
 *
 * @deprecated Use `BaseEntityRestService` against the contract. This adapter is kept for consumers
 * who deploy on Firestore; ProcessPuzzle itself no longer does, so it is no longer exercised by any
 * environment here.
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
 * Nothing about it is broken today and no removal date is set, but only one call site is left to
 * migrate before it can go: `BaseEntityFacade.createService` still returns it when
 * `BACKEND_SERVICE_PROVIDER` is not `rest`. `ApplicationPropertyService` in `base-widget` no longer
 * extends it, and the testbed's own Firestore sample is gone — its entities are served through the
 * contract like every other deployment's.
 *
 * Both of its dependencies on `@angular/fire` are injected rather than imported: the instance as
 * {@link FIRESTORE} rather than the `Firestore` class, and the module functions as {@link FIRESTORE_API}.
 * Each token's own file says which testability problem it solves.
 */
export class BaseEntityFirestoreService<Entity extends BaseEntity> implements BaseEntityService<Entity> {
  protected collection;
  protected readonly firestore = inject(FIRESTORE);
  protected readonly firestoreApi = inject(FIRESTORE_API);

  constructor(
    @Inject('entityMapper') protected entityMapper: BaseEntityMapper<Entity>,
    protected collectionName: string,
  ) {
    this.collection = this.firestoreApi.collection(this.firestore, collectionName);
  }

  // region public accessors and mutators
  add(entity: Entity): Observable<PersistedEntity<Entity>> {
    if (!entity) throw new Error('Entity cant be undefined');
    return from(this.addAsync(entity));
  }

  delete(id: string): Observable<unknown> {
    const docRef = this.firestoreApi.doc(this.firestore, this.collectionName, id);
    return from(this.firestoreApi.deleteDoc(docRef));
  }

  deleteAll(): Observable<unknown> {
    return of(undefined);
  }

  findAll(page?: number, pageSize?: number): Observable<BaseEntityLoadResponse<PersistedEntity<Entity>> | PersistedEntity<Entity>[]> {
    return this.findByQuery({ page, pageSize });
  }

  findById(id: string): Observable<PersistedEntity<Entity> | void> {
    const docRef = this.firestoreApi.doc(this.firestore, this.collectionName, id);
    return from(
      this.firestoreApi.getDoc(docRef)
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
    const newDoc = entity.id ? this.firestoreApi.doc(this.collection, entity.id) : this.firestoreApi.doc(this.collection);
    await this.firestoreApi.setDoc(newDoc, this.mapEntity(entity));
    return this.mapDocument({ ...entity, id: newDoc.id });
  }

  protected createQuery(queryCondition: BaseEntityQueryCondition) {
    let orderBys: QueryOrderByConstraint[] = [];
    if (queryCondition.orderBys?.length) {
      orderBys = queryCondition.orderBys.map((orderByCondition) => {
        const name = Object.keys(OrderByDirection).find((key) => key === orderByCondition.direction);
        const direction = Object.values(OrderByDirection).find((value) => value === name);
        return this.firestoreApi.orderBy(orderByCondition.property, direction);
      });
    }

    let wheres: QueryFieldFilterConstraint[] = [];
    if (queryCondition.filters?.length) {
      wheres = queryCondition.filters.map((filter) => this.firestoreApi.where(filter.property, filter.operator, filter.value));
    }

    return this.firestoreApi.query(this.collection, ...wheres, ...orderBys, this.firestoreApi.limit(queryCondition.pageSize ?? 99));
  }

  protected async findByQueryAsync(queryCondition: BaseEntityQueryCondition): Promise<BaseEntityLoadResponse<PersistedEntity<Entity>> | PersistedEntity<Entity>[]> {
    const builtQuery = this.createQuery(queryCondition);
    const results = await this.firestoreApi.getDocs(builtQuery);
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

    const docRef = this.firestoreApi.doc(this.firestore, this.collectionName, entity.id);
    await this.firestoreApi.updateDoc(docRef, this.mapEntity(entity));

    return entity;
  }

  // endregion
}
