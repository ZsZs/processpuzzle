import { defer, from, Observable, of } from 'rxjs';
import { map } from 'rxjs/operators';
import { BaseEntity, PersistedEntity } from '../base-entity/base-entity';
import { appendRow, asPersisted, EmbeddedRow, findRow, readRows, removeRow, replaceRow, rowId, writeRows } from '../base-entity-embedded/embedded-aggregate';
import { EmbeddedAggregateAccessor, ResolvedEmbeddedAggregate } from '../base-entity-embedded/embedded-aggregate.accessor';
import { BaseEntityService } from './base-entity.service';

/**
 * The repository of an **embedded** entity: one that has no endpoint of its own because its payload travels
 * inside its parent's document.
 *
 * It is the only piece of the embedded story the rest of the framework does not already have. Because
 * `BaseEntityStore` reaches the outside world through exactly this interface, swapping the repository is
 * enough — the child's `BaseEntityListComponent` and `BaseEntityFormComponent`, and the store between them,
 * are the stock ones and know nothing about any of this.
 *
 * A write is a write of the whole root document (see {@link EmbeddedAggregateAccessor.writeRoot}), which is
 * what makes a child's Save persist immediately rather than waiting for the root's form.
 */
export class EmbeddedEntityService<Entity extends BaseEntity> implements BaseEntityService<Entity> {
  constructor(
    private readonly entityName: string,
    private readonly accessor: EmbeddedAggregateAccessor,
  ) {}

  /**
   * The row arrives with its key already set — `createEntity()` mints one for an `id`-bearing child, and a
   * child keyed by something else (`App Region` by `type`) gets it from the form — so there is no
   * server-assigned identity to wait for.
   */
  add(entity: Entity): Observable<PersistedEntity<Entity>> {
    return defer(() => {
      const resolved = this.require();
      const rows = this.rowsOf(resolved);
      return this.commit(resolved, appendRow(rows, entity as EmbeddedRow), asPersisted<Entity>(entity as EmbeddedRow));
    });
  }

  update(entity: PersistedEntity<Entity>): Observable<PersistedEntity<Entity>> {
    return defer(() => {
      const resolved = this.require();
      const rows = this.rowsOf(resolved);
      const id = rowId(entity as EmbeddedRow, resolved.context.referenceIdField);
      return this.commit(resolved, replaceRow(rows, id, entity as EmbeddedRow, resolved.context.referenceIdField), entity);
    });
  }

  delete(id: string): Observable<unknown> {
    return defer(() => {
      const resolved = this.require();
      const rows = this.rowsOf(resolved);
      return this.commit(resolved, removeRow(rows, id, resolved.context.referenceIdField), undefined);
    });
  }

  deleteAll(): Observable<unknown> {
    return defer(() => this.commit(this.require(), [], undefined));
  }

  findAll(): Observable<PersistedEntity<Entity>[]> {
    return defer(() => of(this.rows()));
  }

  /**
   * Ignores the query: an embedded attribute holds the rows of exactly one owner, so there is nothing to
   * page or filter server-side. Returning a bare array puts the store on its unpaged branch, where
   * `totalElements` follows the row count.
   */
  findByQuery(): Observable<PersistedEntity<Entity>[]> {
    return defer(() => of(this.rows()));
  }

  findById(id: string): Observable<PersistedEntity<Entity> | void> {
    return defer(() => {
      const resolved = this.require();
      const row = findRow(this.rowsOf(resolved), id, resolved.context.referenceIdField);
      return of(row === undefined ? undefined : asPersisted<Entity>(row));
    });
  }

  // region protected, private helper methods
  /**
   * Reads without an aggregate are answered with nothing rather than an error: a store's `onInit` load runs
   * as soon as it is injected, which can be before the route that gives it an owner has activated.
   */
  private rows(): PersistedEntity<Entity>[] {
    const resolved = this.accessor.resolve(this.entityName);
    return resolved ? this.rowsOf(resolved).map((row) => asPersisted<Entity>(row)) : [];
  }

  private rowsOf(resolved: ResolvedEmbeddedAggregate): EmbeddedRow[] {
    return readRows(resolved.rootPayload, resolved.context.path, resolved.context.attrName);
  }

  private commit<Result>(resolved: ResolvedEmbeddedAggregate, rows: readonly EmbeddedRow[], result: Result): Observable<Result> {
    const rootPayload = writeRows(resolved.rootPayload, resolved.context.path, resolved.context.attrName, rows);
    return from(this.accessor.writeRoot(resolved, rootPayload)).pipe(map(() => result));
  }

  /** A write without an owner would silently go nowhere, so it names the entity instead. */
  private require(): ResolvedEmbeddedAggregate {
    const resolved = this.accessor.resolve(this.entityName);
    if (!resolved) {
      throw new Error(`'${this.entityName}' is an embedded entity, but no aggregate carrying it is open. Its screens are reachable only through the entity that contains it.`);
    }
    return resolved;
  }
  // endregion
}
