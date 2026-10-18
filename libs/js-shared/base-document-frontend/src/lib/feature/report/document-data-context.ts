import { computed, inject, Injectable, Injector, runInInjectionContext, signal } from '@angular/core';
import { DynamicEntityService, EntityDefinitionRegistry } from '@processpuzzle/base-entity';
import { valueAtPath } from '@processpuzzle/widgets';
import { firstValueFrom } from 'rxjs';
import { DocumentInputPort, PortType } from '../../domain/base-document';

/** What the host hands in per port: an object id for `ENTITY_REF`, an RSQL filter for `ENTITY_COLLECTION`. */
export type DocumentPortValues = Readonly<Record<string, string | undefined>>;

/** One object a parameter can be set to. */
export interface DocumentObjectOption {
  id: string;
  label: string;
}

const OBJECT_OPTIONS_PAGE = 50;

/** `${order.customer.id}` inside a collection filter — a reference to another port's resolved data. */
const PORT_REFERENCE = /\$\{([^}]+)\}/g;

/**
 * The data a document's input ports carry, fetched and held for one rendering — what turns a report template
 * into one report. Provided by the component that renders it (the Preview tab, the Reports tab), and found by
 * `DocumentContentStore.resolveBinding` through DI, so the reader, its widgets and its `dataField` nodes all
 * resolve through the same lookup.
 *
 * - `ENTITY_REF` — the object with the given id, falling back to the port's `defaultValue`;
 * - `ENTITY_COLLECTION` — the objects matching the given filter, falling back to `defaultRsqlFilter`. A filter
 *   may reference another port, `customerId==${customer.id}`; a reference that resolves to nothing leaves the
 *   collection empty instead of running the query without it, which would list everything;
 * - any other port type — the given value, or the port's default, as is.
 *
 * Held in a signal, so whatever reads {@link resolve} inside a `computed` or `effect` re-renders when the data
 * lands. A newer {@link bind} supersedes an older one still in flight: its answers are dropped.
 */
@Injectable()
export class DocumentDataContext {
  private readonly registry = inject(EntityDefinitionRegistry);
  private readonly injector = inject(Injector);
  private readonly services = new Map<string, DynamicEntityService>();
  private readonly data = signal<Readonly<Record<string, unknown>>>({});
  private generation = 0;

  readonly loading = signal(false);
  readonly error = signal<string | undefined>(undefined);
  /** Required ports the host supplied nothing for, and that have no default — the report cannot be complete. */
  readonly missingRequiredPorts = signal<string[]>([]);
  readonly isComplete = computed(() => this.missingRequiredPorts().length === 0);

  /** The value at a port path — `order`, `order.customer.name` — or `undefined` while unbound or absent. */
  resolve(path: string): unknown {
    const dot = path.indexOf('.');
    const port = dot < 0 ? path : path.slice(0, dot);
    return valueAtPath(this.data()[port], dot < 0 ? '' : path.slice(dot + 1));
  }

  async bind(ports: readonly DocumentInputPort[], values: DocumentPortValues): Promise<void> {
    const generation = ++this.generation;
    this.loading.set(true);
    this.error.set(undefined);
    this.missingRequiredPorts.set(ports.filter((port) => port.required && !hasText(values[port.name]) && !hasText(port.defaultValue)).map((port) => port.name));

    try {
      const resolved: Record<string, unknown> = {};
      // References first: a collection's filter may name one, so its query waits for them.
      const references = ports.filter((port) => port.type !== PortType.ENTITY_COLLECTION);
      await Promise.all(references.map(async (port) => (resolved[port.name] = await this.resolvePort(port, values[port.name]))));
      const collections = ports.filter((port) => port.type === PortType.ENTITY_COLLECTION);
      await Promise.all(collections.map(async (port) => (resolved[port.name] = await this.resolveCollection(port, values[port.name], resolved))));
      if (generation === this.generation) this.data.set(resolved);
    } catch (error) {
      if (generation === this.generation) this.error.set(error instanceof Error ? error.message : String(error));
    } finally {
      if (generation === this.generation) this.loading.set(false);
    }
  }

  /**
   * Objects of `entityType` a parameter can be set to, labelled by the attribute that titles an instance —
   * what the parameter bar's picker offers. The first page only: a picker over thousands is a search, later.
   */
  async listObjects(entityType: string | undefined): Promise<DocumentObjectOption[]> {
    const definition = await this.registry.byName(entityType);
    const service = await this.serviceFor(entityType);
    if (!definition || !service) return [];
    const titleAttribute = definition.attributes?.find((attribute) => attribute.isLinkToDetails)?.code ?? definition.attributes?.[0]?.code;
    let objects: unknown[];
    try {
      const response = await firstValueFrom(service.findByQuery({ pageSize: OBJECT_OPTIONS_PAGE }));
      objects = rowsOf(response);
    } catch (error) {
      if (isNoContent(error)) return [];
      throw error;
    }
    return objects
      .map((object) => object as Record<string, unknown>)
      .filter((object) => typeof object['id'] === 'string')
      .map((object) => ({ id: object['id'] as string, label: String((titleAttribute && object[titleAttribute]) ?? object['id']) }));
  }

  private async resolvePort(port: DocumentInputPort, value: string | undefined): Promise<unknown> {
    const given = hasText(value) ? value : port.defaultValue;
    if (port.type !== PortType.ENTITY_REF) return given;
    if (!hasText(given)) return undefined;
    const service = await this.serviceFor(port.entityType);
    if (!service) return undefined;
    return (await firstValueFrom(service.findById(String(given)))) ?? undefined;
  }

  private async resolveCollection(port: DocumentInputPort, filter: string | undefined, resolved: Record<string, unknown>): Promise<unknown[]> {
    const service = await this.serviceFor(port.entityType);
    if (!service) return [];
    const query = substitute(hasText(filter) ? filter : port.defaultRsqlFilter, resolved);
    if (query === null) return [];
    try {
      return rowsOf(await firstValueFrom(service.findByQuery(query ? { query } : {})));
    } catch (error) {
      if (isNoContent(error)) return [];
      throw error;
    }
  }

  /** The repository of the entity type a port names; `entityType` is the definition's *name*, the URL wants its code. */
  private async serviceFor(entityType: string | undefined): Promise<DynamicEntityService | undefined> {
    const definition = await this.registry.byName(entityType);
    if (!definition) return undefined;
    let service = this.services.get(definition.code);
    if (!service) {
      service = runInInjectionContext(this.injector, () => new DynamicEntityService(definition.code));
      this.services.set(definition.code, service);
    }
    return service;
  }
}

/** A page, a bare list or a single object — whichever shape the endpoint answered with — as a list. */
function rowsOf(response: unknown): unknown[] {
  if (Array.isArray(response)) return response;
  if (response && typeof response === 'object' && 'content' in response && Array.isArray(response.content)) return response.content;
  return response ? [response] : [];
}

/** BaseEntityRestService answers an empty result with this error rather than an empty page. */
function isNoContent(error: unknown): boolean {
  return error instanceof Error && error.message === 'The query returned no content.';
}

function hasText(value: unknown): value is string | number {
  return (typeof value === 'string' && value.trim() !== '') || typeof value === 'number';
}

/**
 * `filter` with every `${port.path}` replaced by its resolved value, `undefined` for no filter at all, and
 * `null` when a reference resolves to nothing — the caller then runs no query.
 */
function substitute(filter: string | undefined, resolved: Record<string, unknown>): string | undefined | null {
  if (!filter) return undefined;
  let unresolved = false;
  const query = filter.replace(PORT_REFERENCE, (_match, path: string) => {
    const dot = path.indexOf('.');
    const value = valueAtPath(resolved[dot < 0 ? path : path.slice(0, dot)], dot < 0 ? '' : path.slice(dot + 1));
    if (value === undefined || value === null || typeof value === 'object') {
      unresolved = true;
      return '';
    }
    return String(value as string | number | boolean);
  });
  return unresolved ? null : query;
}
