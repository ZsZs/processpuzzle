import { computed, DestroyRef, inject, Injectable, Signal, signal } from '@angular/core';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseEntityDescriptorRegistry } from '../base-entity-facade/base-entity-descriptor.registry';
import { BaseEntityStoreApi } from '../base-entity-store/base-entity.store';
import { DeleteConfirmationDialog, DeleteConfirmationDialogData } from '../dialogs/delete-confirmation.dialog';
import { DEFAULT_RECENT_CONTEXTS, WORKING_CONTEXT_DEFINITIONS, WorkingContextDefinition } from './working-context.definition';
import { matchesConditions } from './working-context-attributes';
import { readWorkingContextLevels, WORKING_CONTEXT_SEGMENT, WorkingContextRouteLevel } from './working-context.url';

/** A context the URL currently names, with what is known about its entity. */
export interface ActiveWorkingContext {
  name: string;
  id: string;
  definition: WorkingContextDefinition;
  descriptor: BaseEntityDescriptor | undefined;
  /** The entity as its store holds it now — so an edit saved in its form shows at once. `undefined` while loading. */
  entity: Record<string, unknown> | undefined;
  /** The URL of the context's root, `…/ctx/<name>/<id>`. */
  url: string;
}

const STORAGE_PREFIX = 'pp.workingContext.';

/**
 * Holds the active working contexts. **The URL is the source of truth**: what this service reports is read off
 * the current route (`ctx/<name>/<id>/...`), so a refresh, a deep link and a second device need nothing extra.
 * localStorage keeps the last id per context, as the fallback a context view opened without one restores, and the
 * recently active ids the picker offers.
 *
 * Selecting and clearing are navigations: they change the URL, and the route guards and this service follow it.
 */
@Injectable({ providedIn: 'root' })
export class WorkingContextService {
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly descriptorRegistry = inject(BaseEntityDescriptorRegistry);
  private readonly declared = inject(WORKING_CONTEXT_DEFINITIONS).flat();
  private readonly registered = signal<WorkingContextDefinition[]>([]);
  private readonly levels = signal<WorkingContextRouteLevel[]>([]);

  /** Every declared context. A later registration of a name replaces an earlier one. */
  readonly definitions: Signal<WorkingContextDefinition[]> = computed(() => {
    const byName = new Map<string, WorkingContextDefinition>();
    for (const definition of [...this.declared, ...this.registered()]) byName.set(definition.name, definition);
    return [...byName.values()];
  });

  /** The contexts the URL names an entity for, outermost first. */
  readonly active: Signal<ActiveWorkingContext[]> = computed(() =>
    this.levels()
      .filter((level): level is WorkingContextRouteLevel & { id: string } => level.id !== undefined)
      .flatMap((level) => {
        const definition = this.definitionOf(level.name);
        if (!definition) return [];
        const store = this.storeOf(definition);
        const entity = store?.entities().find((candidate) => candidate.id === level.id) as Record<string, unknown> | undefined;
        return [{ name: level.name, id: level.id, definition, descriptor: this.descriptorRegistry.getDescriptor(definition.entityName), entity, url: level.url }];
      }),
  );

  constructor() {
    const subscription = this.router.events.subscribe((event) => {
      if (event instanceof NavigationEnd) this.readUrl();
    });
    inject(DestroyRef).onDestroy(() => subscription.unsubscribe());
    this.readUrl();
  }

  // region definitions
  /** Adds contexts declared at run-time — by an application definition loaded after bootstrap. Returns the undo. */
  register(definitions: WorkingContextDefinition[]): () => void {
    this.registered.update((current) => [...current, ...definitions]);
    return () => this.registered.update((current) => current.filter((candidate) => !definitions.includes(candidate)));
  }

  definitionOf(name: string): WorkingContextDefinition | undefined {
    return this.definitions().find((definition) => definition.name === name);
  }

  /** The contexts an entity type is the root of — what decides whether its list offers "Set as active". */
  definitionsForEntity(entityName: string): WorkingContextDefinition[] {
    return this.definitions().filter((definition) => definition.entityName === entityName);
  }
  // endregion

  // region active context
  current(name: string): ActiveWorkingContext | undefined {
    return this.active().find((context) => context.name === name);
  }

  /** The entity behind an id, from its store or — when the store does not hold it yet — loaded into it. */
  async load(name: string, id: string): Promise<Record<string, unknown> | undefined> {
    const definition = this.definitionOf(name);
    const store = definition ? this.storeOf(definition) : undefined;
    if (!store) return undefined;
    const held = store.entities().find((candidate) => candidate.id === id);
    return (held ?? (await store.reload(id))) as Record<string, unknown> | undefined;
  }

  /**
   * Makes `id` the active entity of the context, by navigating to it. Inside the context the user stays on the
   * view they are on; from outside it they land on the context's first view. Selecting a parent drops its child
   * contexts from the URL, because their entities belong to the parent being left.
   *
   * Resolves `false` when the user declined the switch, or when the context cannot be addressed from here — a
   * child context while its parent is not active.
   */
  async select(name: string, id: string): Promise<boolean> {
    const definition = this.definitionOf(name);
    if (!definition) return false;
    const current = this.current(name);
    if (current?.id === id) return this.router.navigateByUrl(current.url);
    if (current && !(await this.confirmLeaving(current))) return false;

    const url = this.urlFor(definition, id);
    if (url === undefined) return false;
    this.remember(name, id);
    return this.router.navigateByUrl(url);
  }

  /** Leaves the context: the view stays, showing its empty state. The stored fallback is dropped with it. */
  async clear(name: string): Promise<boolean> {
    const level = this.levels().find((candidate) => candidate.name === name);
    const current = this.current(name);
    if (!level) return false;
    if (current && !(await this.confirmLeaving(current))) return false;

    this.forgetLast(name);
    return this.router.navigateByUrl(`${level.baseUrl}/${WORKING_CONTEXT_SEGMENT}/${name}${level.id === undefined ? level.rest : this.viewOf(level.rest)}`);
  }

  /** Whether leaving the active entity of the context should be confirmed first — see `confirmSwitchWhen`. */
  needsConfirmation(context: ActiveWorkingContext): boolean {
    return matchesConditions(context.entity, context.definition.confirmSwitchWhen, context.descriptor);
  }
  // endregion

  // region persistence
  /** The ids of the context's recently active entities, most recent first. */
  recent(name: string): string[] {
    const limit = this.definitionOf(name)?.picker?.recent ?? DEFAULT_RECENT_CONTEXTS;
    return this.readList(name, 'recent').slice(0, limit);
  }

  /** The id a context view opened without a context restores, when the context persists to localStorage. */
  lastId(name: string): string | undefined {
    if (this.definitionOf(name)?.persist === 'url') return undefined;
    return this.storage()?.getItem(STORAGE_PREFIX + name + '.last') ?? undefined;
  }

  remember(name: string, id: string): void {
    const storage = this.storage();
    if (!storage) return;
    storage.setItem(STORAGE_PREFIX + name + '.last', id);
    const recent = [id, ...this.readList(name, 'recent').filter((candidate) => candidate !== id)].slice(0, 10);
    storage.setItem(STORAGE_PREFIX + name + '.recent', JSON.stringify(recent));
  }

  /** Drops an id that no longer resolves, so neither the fallback nor the picker offers it again. */
  forget(name: string, id: string): void {
    const storage = this.storage();
    if (!storage) return;
    if (storage.getItem(STORAGE_PREFIX + name + '.last') === id) this.forgetLast(name);
    storage.setItem(STORAGE_PREFIX + name + '.recent', JSON.stringify(this.readList(name, 'recent').filter((candidate) => candidate !== id)));
  }
  // endregion

  // region private helper methods
  private readUrl(): void {
    let snapshot: ActivatedRouteSnapshot | null = this.router.routerState.snapshot.root;
    while (snapshot?.firstChild) snapshot = snapshot.firstChild;
    this.levels.set(readWorkingContextLevels(snapshot));
  }

  private storeOf(definition: WorkingContextDefinition): BaseEntityStoreApi<BaseEntity> | undefined {
    return this.descriptorRegistry.getStore<BaseEntityStoreApi<BaseEntity>>(definition.entityName);
  }

  private urlFor(definition: WorkingContextDefinition, id: string): string | undefined {
    const segment = `/${WORKING_CONTEXT_SEGMENT}/${definition.name}/${encodeURIComponent(id)}`;
    const level = this.levels().find((candidate) => candidate.name === definition.name);
    // The empty state keeps the whole view the user asked for; switching from another entity keeps only the
    // view's first segment, since anything below it addressed the entity being left.
    if (level) return level.baseUrl + segment + (level.id === undefined ? level.rest : this.viewOf(level.rest));

    if (definition.parent) {
      const parent = this.levels().find((candidate) => candidate.name === definition.parent && candidate.id !== undefined);
      return parent ? parent.url + segment : undefined;
    }
    return (definition.basePath ?? '').replace(/\/$/, '') + segment;
  }

  /** The first segment of the view inside a context — not a nested `ctx`, which belongs to the entity being left. */
  private viewOf(rest: string): string {
    const first = rest.split('/')[1];
    return first && first !== WORKING_CONTEXT_SEGMENT ? '/' + first : '';
  }

  private async confirmLeaving(context: ActiveWorkingContext): Promise<boolean> {
    if (!this.needsConfirmation(context)) return true;
    const data: DeleteConfirmationDialogData = {
      titleKey: 'base_entity.working_context.confirm_switch.title',
      contentKey: 'base_entity.working_context.confirm_switch.content',
      contentParams: { entity: String(context.entity?.[context.descriptor?.titleAttrName() ?? 'id'] ?? context.id) },
      cancelButtonKey: 'base_entity.working_context.confirm_switch.cancel_button',
      confirmButtonKey: 'base_entity.working_context.confirm_switch.confirm_button',
    };
    const confirmed = await firstValueFrom(this.dialog.open(DeleteConfirmationDialog, { data }).afterClosed());
    return confirmed === true;
  }

  private forgetLast(name: string): void {
    this.storage()?.removeItem(STORAGE_PREFIX + name + '.last');
  }

  private readList(name: string, key: string): string[] {
    try {
      const parsed = JSON.parse(this.storage()?.getItem(STORAGE_PREFIX + name + '.' + key) ?? '[]');
      return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : [];
    } catch {
      return [];
    }
  }

  /** localStorage, or nothing where there is none (server rendering) or it is blocked. */
  private storage(): Storage | undefined {
    try {
      return typeof localStorage === 'undefined' ? undefined : localStorage;
    } catch {
      return undefined;
    }
  }
  // endregion
}
