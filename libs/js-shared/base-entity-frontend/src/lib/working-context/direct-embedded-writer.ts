import { inject, Injectable } from '@angular/core';
import { EmbeddedRow, readRows, rowId } from '../base-entity-embedded/embedded-aggregate';
import { ResolvedEmbeddedAggregate } from '../base-entity-embedded/embedded-aggregate.accessor';
import { CollectionItem, EmbeddedCollectionService, itemVersionOf, mergePatch } from './embedded-collection.service';

/**
 * Saves an embedded row **straight through the item endpoints**, for an aggregate whose root is a working context.
 *
 * An entity's own form stages a row in the whole document and the form's Save persists it. A working context has no form
 * of the whole entity — that is the point of it — so there is nothing to Save: each row goes to the server as the row, under
 * its own version, and the root is read again so every screen shows what is stored.
 *
 * The backend addresses a collection one level below the entity's own, and the row inside it, by `id`. A row nested deeper
 * has no endpoint yet, and a row stored before ids existed has none to address it by; both say so rather than guess.
 */
@Injectable({ providedIn: 'root' })
export class DirectEmbeddedWriter {
  private readonly collections = inject(EmbeddedCollectionService);

  /** Whether this aggregate is saved here rather than staged in the root document. */
  handles(resolved: ResolvedEmbeddedAggregate): boolean {
    return resolved.context.viaWorkingContext !== undefined;
  }

  /** The row as stored: with the `id` and `itemVersion` the server assigned. */
  async append(resolved: ResolvedEmbeddedAggregate, row: EmbeddedRow): Promise<CollectionItem> {
    const { entity, id, attr } = this.target(resolved);
    const owner = this.ownerOf(resolved);
    const stored = owner
      ? await this.collections.appendNested(entity, id, owner.attrName, owner.itemId, attr, row as CollectionItem)
      : await this.collections.append(entity, id, attr, row as CollectionItem);
    await this.refresh(resolved);
    return stored;
  }

  /** Sends only what changed, against the row's own version; the row unchanged when nothing did. */
  async patch(resolved: ResolvedEmbeddedAggregate, before: EmbeddedRow, after: EmbeddedRow): Promise<CollectionItem> {
    const patch = mergePatch(before as CollectionItem, after as CollectionItem);
    if (Object.keys(patch).length === 0) return after as CollectionItem;

    const { entity, id, attr } = this.target(resolved);
    const itemId = this.itemIdOf(before, resolved);
    const version = itemVersionOf(before as CollectionItem);
    const owner = this.ownerOf(resolved);
    const stored = owner
      ? await this.collections.patchNested(entity, id, owner.attrName, owner.itemId, attr, itemId, patch, version)
      : await this.collections.patch(entity, id, attr, itemId, patch, version);
    await this.refresh(resolved);
    return stored;
  }

  async remove(resolved: ResolvedEmbeddedAggregate, before: EmbeddedRow): Promise<void> {
    const { entity, id, attr } = this.target(resolved);
    const itemId = this.itemIdOf(before, resolved);
    const version = itemVersionOf(before as CollectionItem);
    const owner = this.ownerOf(resolved);
    if (owner) await this.collections.removeNested(entity, id, owner.attrName, owner.itemId, attr, itemId, version);
    else await this.collections.remove(entity, id, attr, itemId, version);
    await this.refresh(resolved);
  }

  // region private helper methods
  private target(resolved: ResolvedEmbeddedAggregate): { entity: string; id: string; attr: string } {
    if (resolved.context.path.length > 1) {
      throw new Error(`'${resolved.context.entityName}' is nested too deep to be saved on its own: the server addresses a collection one level below the entity's.`);
    }
    return { entity: resolved.context.rootEntityName, id: resolved.context.rootId, attr: resolved.context.attrName };
  }

  /** The row the collection is nested in, when it is: its attribute at the top, and its `id`. */
  private ownerOf(resolved: ResolvedEmbeddedAggregate): { attrName: string; itemId: string } | undefined {
    const step = resolved.context.path[0];
    if (!step) return undefined;
    const owner = readRows(resolved.rootPayload, [], step.attrName)[step.index];
    return { attrName: step.attrName, itemId: this.itemIdOf(owner, resolved) };
  }

  private itemIdOf(row: EmbeddedRow | undefined, resolved: ResolvedEmbeddedAggregate): string {
    const id = row ? rowId(row) : '';
    if (!id) {
      throw new Error(`This ${resolved.context.entityName} has no id yet, which is what it is saved by. Save its ${resolved.context.rootEntityName} once from its own form first.`);
    }
    return id;
  }

  /** Reads the root again, so the rows every screen lists are the stored ones. */
  private async refresh(resolved: ResolvedEmbeddedAggregate): Promise<void> {
    await resolved.rootStore.reload(resolved.context.rootId);
  }
  // endregion
}
