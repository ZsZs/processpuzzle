import { inject, Injectable } from '@angular/core';
import { EntityDefinitionRegistry, snakeCaseName } from '@processpuzzle/base-entity';
import { firstValueFrom } from 'rxjs';
import { StateMachineDefinition } from './state-machine-definition';
import { StateMachineDefinitionService } from './state-machine-definition.service';

/**
 * Which of the organization's entity types are governed by a state machine, and under which key.
 *
 * **Two names for one entity.** A `StateMachineDefinition.entityName` is the entity *definition code* —
 * `order`, `dynamic-entity` — because that is what base-entity's instances resource is addressed by and
 * what `EntityObjectCreatedEvent.entityDefinitionCode()` carries. A `BaseEntityDescriptor.entityName` is the
 * definition's display *name* — `Order`. Everything that asks this registry a question holds a descriptor,
 * so the translation happens here, once, rather than at each call site guessing which of the two it has.
 *
 * `EntityDefinitionRegistry` is the authority for that translation and is consulted first. `snakeCaseName`
 * is the fallback, for an entity that is compiled in rather than defined as metadata and therefore has no
 * definition row at all: it is the same rule the URL segments follow, so `Special Order` yields
 * `special-order` either way. A fallback rather than the only rule, because a definition is free to have a
 * code its name does not snake-case to, and silently governing the wrong entity is worse than not
 * governing it.
 *
 * **Loaded once.** The machines of an organization are metadata that changes when someone edits them in the
 * modeler, not while a user is reading a form, so the list is fetched on first question and cached. Call
 * {@link reset} after authoring a machine — the same contract `EntityDefinitionRegistry.reset` has, and for
 * the same reason.
 */
@Injectable({ providedIn: 'root' })
export class GovernedEntityRegistry {
  private readonly service = inject(StateMachineDefinitionService);
  private readonly definitions = inject(EntityDefinitionRegistry);
  private machines?: Promise<ReadonlyMap<string, StateMachineDefinition>>;

  /** The machine governing the entity a descriptor names, or `undefined` — by far the common answer. */
  async machineFor(descriptorEntityName: string | undefined): Promise<StateMachineDefinition | undefined> {
    const key = await this.machineKeyOf(descriptorEntityName);
    return key ? (await this.load()).get(key) : undefined;
  }

  /** Whether any machine governs the entity a descriptor names. */
  async governs(descriptorEntityName: string | undefined): Promise<boolean> {
    return (await this.machineFor(descriptorEntityName)) !== undefined;
  }

  /**
   * The key the entity a descriptor names is addressed by in base-state — its definition code — or
   * `undefined` when the name is empty. Answers even for an entity no machine governs: the caller may be
   * about to ask the operation layer about it, and "no machine" is that endpoint's own 404.
   */
  async machineKeyOf(descriptorEntityName: string | undefined): Promise<string | undefined> {
    if (!descriptorEntityName) return undefined;
    const definition = await this.definitions.byName(descriptorEntityName).catch(() => undefined);
    return definition?.code ?? snakeCaseName(descriptorEntityName);
  }

  /** Discards the cached machines, so the next question fetches again. */
  reset(): void {
    this.machines = undefined;
  }

  private load(): Promise<ReadonlyMap<string, StateMachineDefinition>> {
    this.machines ??= this.fetch();
    return this.machines;
  }

  /**
   * Every machine of the organization, by `entityName`.
   *
   * A failure yields an empty map rather than a rejected promise, and the cache is dropped so the next
   * question retries. base-state may not be deployed at all in a given topology, and an application whose
   * entity screens refused to mount because an optional feature's endpoint answered 500 would be trading a
   * missing tab for a broken form.
   */
  private async fetch(): Promise<ReadonlyMap<string, StateMachineDefinition>> {
    try {
      const response = await firstValueFrom(this.service.findAll());
      return new Map(unwrap(response).map((machine) => [machine.entityName, machine]));
    } catch {
      this.machines = undefined;
      return new Map();
    }
  }
}

/** The rows of a response that may be a page, a bare array or — with json-server — a single record. */
function unwrap(response: unknown): StateMachineDefinition[] {
  if (Array.isArray(response)) return response as StateMachineDefinition[];
  if (response && typeof response === 'object' && 'content' in response) return (response as { content: StateMachineDefinition[] }).content ?? [];
  return response ? [response as StateMachineDefinition] : [];
}
