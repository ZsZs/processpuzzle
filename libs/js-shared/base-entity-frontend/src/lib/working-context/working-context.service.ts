import { computed, DestroyRef, effect, inject, Injectable, Signal, signal, untracked } from '@angular/core';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseEntityDescriptorRegistry } from '../base-entity-facade/base-entity-descriptor.registry';
import { BaseEntityStoreApi } from '../base-entity-store/base-entity.store';
import { DeleteConfirmationDialog, DeleteConfirmationDialogData } from '../dialogs/delete-confirmation.dialog';
import { DEFAULT_RECENT_CONTEXTS, WORKING_CONTEXT_DEFINITIONS, WorkingContextDefinition } from './working-context.definition';
import { ENTITY_STATE_ACTIONS, EntityStateSnapshot } from './entity-state-actions';
import { matchesConditions } from './working-context-attributes';
import { EntityRouteRegistry } from '../base-form-navigator/entity-route.registry';
import { snakeCaseName } from '../base-form-navigator/base-form-navigator.store';
import { readWorkingContextLevels, UNKNOWN_CONTEXT_OBJECT_ID, WORKING_CONTEXT_SEGMENT, WorkingContextRouteLevel } from './working-context.url';

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
  private readonly stateActions = inject(ENTITY_STATE_ACTIONS, { optional: true });
  private readonly snackBar = inject(MatSnackBar);
  /** What is known of each active entity's state, by `<entity>/<id>`, with the entity version it was read at. */
  private readonly snapshots = signal<Record<string, { version: unknown; snapshot: EntityStateSnapshot | undefined }>>({});
  private readonly snapshotsLoading = new Set<string>();
  private readonly entityRoutes = inject(EntityRouteRegistry);
  /** Where the user was heading when no context object was known, by context name — see {@link rememberTarget}. */
  private readonly pendingTargets = signal<Record<string, string>>({});

  /** Bumped whenever the remembered ids change, so {@link lastId} can be read reactively. */
  private readonly rememberedVersion = signal(0);

  /** True while a transition is being fired, so a second click does not fire another. */
  readonly firing = signal(false);

  /**
   * True while an application shell shows the contexts in a status bar of its own. The status bar inside the
   * entity tabs then leaves them out rather than showing them a second time.
   */
  readonly statusbarHostedByShell = signal(false);

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
    // Follows the active entities, and their saved changes: a new version is a new question to the state machine.
    effect(() => {
      for (const context of this.active()) untracked(() => void this.loadState(context));
    });
  }

  // region definitions
  /** Adds contexts declared at run-time — by an application definition loaded after bootstrap. Returns the undo. */
  register(definitions: WorkingContextDefinition[]): () => void {
    this.registered.update((current) => [...current, ...definitions]);
    return () => this.registered.update((current) => current.filter((candidate) => !definitions.includes(candidate)));
  }

  /**
   * The context of an entity that `canContextObject`: named like its URL segment, and addressed by its own URL
   * (`<aggregate>/<id>/<embedded>/list`) rather than a `ctx` segment. Created on first ask and then kept.
   */
  ensureAggregate(entityName: string): WorkingContextDefinition {
    const name = snakeCaseName(entityName);
    const existing = this.definitionOf(name);
    if (existing) return existing;

    const definition: WorkingContextDefinition = { name, entityName, aggregate: true };
    this.registered.update((current) => [...current, definition]);
    return definition;
  }

  /** Remembers the URL a user asked for while no context object was known; {@link takeTarget} hands it back once one is chosen. */
  rememberTarget(name: string, url: string): void {
    this.pendingTargets.update((current) => ({ ...current, [name]: url }));
  }

  /** The URL waiting for a context object, without consuming it — the list says where the user is headed. */
  pendingTarget(name: string): string | undefined {
    return this.pendingTargets()[name];
  }

  private takeTarget(name: string): string | undefined {
    const url = this.pendingTargets()[name];
    if (url !== undefined) this.pendingTargets.update(({ [name]: _taken, ...rest }) => rest);
    return url;
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

    const url = definition.aggregate ? this.aggregateUrlFor(definition, id) : this.urlFor(definition, id);
    if (url === undefined) return false;
    this.remember(name, id);
    // A context object chosen from its own list with no page waiting is only remembered: the user stays on the list.
    return url === null ? true : this.router.navigateByUrl(url);
  }

  /** Leaves the context: the view stays, showing its empty state. The stored fallback is dropped with it. */
  async clear(name: string): Promise<boolean> {
    const level = this.levels().find((candidate) => candidate.name === name);
    const current = this.current(name);
    if (!level) return false;
    if (current && !(await this.confirmLeaving(current))) return false;

    this.forgetLast(name);
    // A context object's URL keeps its view and asks for the object again; its guard sends the user to the list to choose.
    if (level.aggregate) return this.router.navigateByUrl(`${level.baseUrl}/${UNKNOWN_CONTEXT_OBJECT_ID}${level.rest}`);
    return this.router.navigateByUrl(`${level.baseUrl}/${WORKING_CONTEXT_SEGMENT}/${name}${level.id === undefined ? level.rest : this.viewOf(level.rest)}`);
  }

  /** Whether leaving the active entity of the context should be confirmed first — see `confirmSwitchWhen`. */
  needsConfirmation(context: ActiveWorkingContext): boolean {
    return matchesConditions(context.entity, context.definition.confirmSwitchWhen, context.descriptor, this.stateAttributeOf(context));
  }
  // endregion

  // region state
  /** The active entity's state and the transitions it can take; `undefined` until known, or when no state machine governs it. */
  stateOf(name: string): EntityStateSnapshot | undefined {
    const context = this.current(name);
    return context ? this.snapshots()[this.stateKey(context)]?.snapshot : undefined;
  }

  /** The attribute holding the entity's state, as its state machine names it — what `state` in a declaration means. */
  stateAttributeOf(context: ActiveWorkingContext): string | undefined {
    return this.snapshots()[this.stateKey(context)]?.snapshot?.stateAttribute;
  }

  /**
   * Fires a transition of the active entity of the context, tells the user what came of it and reloads the entity —
   * which refreshes the badge and the transitions on offer, since both follow the entity's version.
   * Resolves `true` when the transition ran.
   */
  async fire(name: string, triggerKey: string): Promise<boolean> {
    const context = this.current(name);
    if (!context || !this.stateActions || this.firing()) return false;
    this.firing.set(true);
    try {
      const version = Number((context.entity as { version?: number } | undefined)?.version ?? 0);
      const result = await this.stateActions.fire(context.definition.entityName, context.id, triggerKey, version);
      this.snackBar.open(result.message, undefined, { duration: 4000 });
      await this.storeOf(context.definition)?.reload(context.id);
      return result.success;
    } finally {
      this.firing.set(false);
    }
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
    this.rememberedVersion();
    if (this.definitionOf(name)?.persist === 'url') return undefined;
    return this.storage()?.getItem(STORAGE_PREFIX + name + '.last') ?? undefined;
  }

  remember(name: string, id: string): void {
    const storage = this.storage();
    if (!storage) return;
    storage.setItem(STORAGE_PREFIX + name + '.last', id);
    const recent = [id, ...this.readList(name, 'recent').filter((candidate) => candidate !== id)].slice(0, 10);
    storage.setItem(STORAGE_PREFIX + name + '.recent', JSON.stringify(recent));
    this.rememberedVersion.update((version) => version + 1);
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
  private stateKey(context: ActiveWorkingContext): string {
    return `${context.definition.entityName}/${context.id}`;
  }

  private async loadState(context: ActiveWorkingContext): Promise<void> {
    if (!this.stateActions || !context.entity) return;
    const key = this.stateKey(context);
    const version = (context.entity as { version?: unknown }).version;
    const loadKey = `${key}@${String(version)}`;
    if (this.snapshots()[key]?.version === version || this.snapshotsLoading.has(loadKey)) return;

    this.snapshotsLoading.add(loadKey);
    try {
      const snapshot = await this.stateActions.stateOf(context.definition.entityName, context.id).catch(() => undefined);
      this.snapshots.update((current) => ({ ...current, [key]: { version, snapshot } }));
    } finally {
      this.snapshotsLoading.delete(loadKey);
    }
  }

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

  /**
   * Where choosing `id` leads: back to the URL the user was refused at while none was known; otherwise the view they are on,
   * for another object; otherwise `null` — nowhere, the choice is only remembered.
   */
  private aggregateUrlFor(definition: WorkingContextDefinition, id: string): string | null | undefined {
    const segment = encodeURIComponent(id);
    const target = this.takeTarget(definition.name);
    if (target) return target.replace(`/${UNKNOWN_CONTEXT_OBJECT_ID}`, `/${segment}`);

    const level = this.levels().find((candidate) => candidate.name === definition.name && candidate.aggregate);
    // Switching keeps the kind of part being listed, not a row of the object being left.
    if (level) return `${level.baseUrl}/${segment}${this.viewOf(level.rest) ? this.viewOf(level.rest) + '/list' : ''}`;

    // Outside the context (the aggregate's own list), with nowhere asked for: remember only.
    return null;
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
    this.rememberedVersion.update((version) => version + 1);
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
