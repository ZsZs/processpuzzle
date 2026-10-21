import { inject, Injectable } from '@angular/core';
import { EntityDefinitionRegistry, snakeCaseName } from '@processpuzzle/base-entity';
import { firstValueFrom } from 'rxjs';
import type { CalculationOutput } from './calculation-api';
import { CalculationApiService } from './calculation-api.service';

/**
 * Which attributes of an entity a released calculation writes, and how — fetched once per entity and cached.
 *
 * **Two names for one entity.** The outputs are addressed by the entity *definition code* (`order`); a
 * `BaseEntityDescriptor` holds the display *name* (`Order`). `EntityDefinitionRegistry` translates, with
 * `snakeCaseName` as the fallback for a compiled-in entity that has no definition row — the rule
 * base-state's `GovernedEntityRegistry.machineKeyOf` follows, for the same reasons.
 *
 * A failure yields no outputs rather than a rejected promise, and the cache entry is dropped so the next
 * question retries: base-calculation may not be deployed at all, and a form must not refuse to mount for it.
 * Call {@link reset} after releasing calculations.
 */
@Injectable({ providedIn: 'root' })
export class CalculationOutputRegistry {
  private readonly api = inject(CalculationApiService);
  private readonly definitions = inject(EntityDefinitionRegistry);
  private readonly cache = new Map<string, Promise<CalculationOutput[]>>();

  /** What calculations write into the entity a descriptor names — usually nothing. */
  async outputsOf(descriptorEntityName: string | undefined): Promise<CalculationOutput[]> {
    const code = await this.entityCodeOf(descriptorEntityName);
    return code ? this.load(code) : [];
  }

  /** The output writing `attribute`, if any. */
  async outputFor(descriptorEntityName: string | undefined, attribute: string): Promise<CalculationOutput | undefined> {
    return (await this.outputsOf(descriptorEntityName)).find((output) => output.attribute === attribute);
  }

  /** Discards the cached outputs, so the next question fetches again. */
  reset(): void {
    this.cache.clear();
  }

  private async entityCodeOf(descriptorEntityName: string | undefined): Promise<string | undefined> {
    if (!descriptorEntityName) return undefined;
    const definition = await this.definitions.byName(descriptorEntityName).catch(() => undefined);
    return definition?.code ?? snakeCaseName(descriptorEntityName);
  }

  private load(code: string): Promise<CalculationOutput[]> {
    let outputs = this.cache.get(code);
    if (!outputs) {
      outputs = this.fetch(code);
      this.cache.set(code, outputs);
    }
    return outputs;
  }

  private async fetch(code: string): Promise<CalculationOutput[]> {
    try {
      return await firstValueFrom(this.api.outputs(code));
    } catch {
      this.cache.delete(code);
      return [];
    }
  }
}
