import { Injectable } from '@angular/core';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';

/** One resolved entity as the lookup surface needs it: what renders it, and where its rows live. */
interface ResolvedEntry {
  descriptor: BaseEntityDescriptor;
  store: unknown;
}

/**
 * Descriptors and stores of the entities {@link DynamicEntityRegistry} has synthesized, published here for
 * `BaseEntityDescriptorRegistry` to read.
 *
 * **A cache with no dependencies, on purpose.** The lookup surface has to be able to answer for a
 * metadata-defined entity, but it must not drag in the machinery that *builds* one: a facade pulls in the
 * REST and Firestore repositories, and the descriptor registry is injected by half the library — including
 * `EmbeddedAggregateAccessor`, which every embedded form control reaches. Publishing into a plain map keeps
 * that edge from existing at all, and keeps the direction of the dependency the honest one: the registry
 * that builds entities knows about the registry that looks them up, not the reverse.
 *
 * The store is held as `unknown` for the same reason `BaseEntityDescriptor.store` is: a signal store's type
 * is produced by `signalStore()` at run-time and there is no interface here to name it by.
 */
@Injectable({ providedIn: 'root' })
export class ResolvedEntityCache {
  private readonly entries = new Map<string, ResolvedEntry>();

  register(entityName: string, entry: ResolvedEntry): void {
    this.entries.set(entityName, entry);
  }

  descriptorOf(entityName: string | undefined): BaseEntityDescriptor | undefined {
    return entityName ? this.entries.get(entityName)?.descriptor : undefined;
  }

  storeOf(entityName: string | undefined): unknown {
    return entityName ? this.entries.get(entityName)?.store : undefined;
  }

  has(entityName: string | undefined): boolean {
    return !!entityName && this.entries.has(entityName);
  }

  clear(): void {
    this.entries.clear();
  }
}
