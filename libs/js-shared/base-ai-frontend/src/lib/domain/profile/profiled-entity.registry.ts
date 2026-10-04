import { inject, Injectable } from '@angular/core';
import { EntityDefinitionRegistry, snakeCaseName } from '@processpuzzle/base-entity';
import { firstValueFrom } from 'rxjs';
import { RecognitionProfile } from './recognition-profile';
import { RecognitionProfileService } from './recognition-profile.service';

/**
 * Which of the organization's entity types have a recognition profile, and under which key.
 *
 * The same two-names problem `GovernedEntityRegistry` solves for base-state: a profile's `entityName` is the
 * entity *definition code* (`boat`) — what the enrollment endpoints are addressed by — while a descriptor on
 * the screen names the entity by its display name (`Boat`). `EntityDefinitionRegistry` translates, and
 * `snakeCaseName` is the fallback for an entity compiled into the application rather than defined as
 * metadata.
 *
 * Loaded once and cached; call {@link reset} after authoring a profile. A failure yields "no profiles"
 * rather than an error, so that an application without the AI backend still renders every entity screen.
 */
@Injectable({ providedIn: 'root' })
export class ProfiledEntityRegistry {
  private readonly service = inject(RecognitionProfileService);
  private readonly definitions = inject(EntityDefinitionRegistry);
  private profiles?: Promise<ReadonlyMap<string, RecognitionProfile>>;

  /** The profile of the entity a descriptor names, or `undefined` — the usual answer. */
  async profileFor(descriptorEntityName: string | undefined): Promise<RecognitionProfile | undefined> {
    const key = await this.keyOf(descriptorEntityName);
    return key ? (await this.load()).get(key) : undefined;
  }

  /** The definition code the entity a descriptor names is addressed by in base-ai. */
  async keyOf(descriptorEntityName: string | undefined): Promise<string | undefined> {
    if (!descriptorEntityName) return undefined;
    const definition = await this.definitions.byName(descriptorEntityName).catch(() => undefined);
    return definition?.code ?? snakeCaseName(descriptorEntityName);
  }

  reset(): void {
    this.profiles = undefined;
  }

  private load(): Promise<ReadonlyMap<string, RecognitionProfile>> {
    this.profiles ??= this.fetch();
    return this.profiles;
  }

  private async fetch(): Promise<ReadonlyMap<string, RecognitionProfile>> {
    try {
      const response = await firstValueFrom(this.service.findAll());
      return new Map(unwrap(response).map((profile) => [profile.entityName, profile]));
    } catch {
      this.profiles = undefined;
      return new Map();
    }
  }
}

/** The rows of a response that may be a page, a bare array or — with json-server — a single record. */
function unwrap(response: unknown): RecognitionProfile[] {
  if (Array.isArray(response)) return response as RecognitionProfile[];
  if (response && typeof response === 'object' && 'content' in response) return (response as { content: RecognitionProfile[] }).content ?? [];
  return response ? [response as RecognitionProfile] : [];
}
