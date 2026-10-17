import { inject, Injectable, signal } from '@angular/core';
import { EmbeddedAggregateAccessor, EntityDefinition, EntityDefinitionService, type Selectable } from '@processpuzzle/base-entity';
import { firstValueFrom } from 'rxjs';
import { EventDefinition, EventKind } from '../domain/event-definition';
import { EVENT_PARAMETER_ENTITY_NAME } from '../domain/event-entity-names';

/** What an `ENTITY_REF` parameter may name besides an entity definition: base-document's documents. */
export const DOCUMENT_ENTITY_TYPE = 'document';

/**
 * The organization's entity types, and their attributes, as options for the event catalog's form: the
 * definition's Subject Type, a parameter's Entity Type and — for a SYSTEM event — the parameter's Name,
 * which has to be an attribute of the subject or the save is refused.
 *
 * Read from the template on every change detection, so synchronous: the first call starts the fetch and the
 * options grow when it lands. A failed fetch offers nothing, and the fields stay free text.
 */
@Injectable({ providedIn: 'root' })
export class EventDefinitionOptions {
  private readonly service = inject(EntityDefinitionService);
  private readonly accessor = inject(EmbeddedAggregateAccessor);
  private readonly fetched = signal<readonly EntityDefinition[]>([]);
  private loading?: Promise<void>;

  /** Every entity definition's code, alphabetically. */
  entityCodes(): Selectable[] {
    return options(this.definitions().map((definition) => definition.code));
  }

  /** What an `ENTITY_REF` parameter can reference: the entity codes and `document`. */
  referenceTypes(): Selectable[] {
    return options([...this.definitions().map((definition) => definition.code), DOCUMENT_ENTITY_TYPE]);
  }

  /**
   * The attribute codes of the subject of the event definition being edited — what a SYSTEM event's
   * parameter is filled from, by name. None for a MESSAGE or SIGNAL, whose parameters are named freely.
   */
  subjectAttributes(): Selectable[] {
    const owner = this.accessor.resolve(EVENT_PARAMETER_ENTITY_NAME)?.rootPayload as EventDefinition | undefined;
    if (owner?.kind !== EventKind.SYSTEM || !owner.subjectType) return [];
    const subject = this.definitions().find((definition) => definition.code === owner.subjectType);
    return options((subject?.attributes ?? []).map((attribute) => attribute.code));
  }

  private definitions(): readonly EntityDefinition[] {
    this.loading ??= firstValueFrom(this.service.findAll()).then(
      (definitions) => this.fetched.set(definitions),
      () => undefined,
    );
    return this.fetched();
  }
}

function options(values: Array<string | undefined>): Selectable[] {
  const unique = [...new Set(values.filter((value): value is string => !!value))];
  return unique.sort((left, right) => left.localeCompare(right)).map((value) => ({ key: value, value }));
}
