import { inject, Injectable, signal } from '@angular/core';
import { EntityDefinition, EntityDefinitionService, type Selectable } from '@processpuzzle/base-entity';
import { firstValueFrom } from 'rxjs';

/**
 * The organization's entity types, as options for a chat definition's Subject Definition.
 *
 * Read from the template on every change detection, so synchronous: the first call starts the fetch and the
 * options grow when it lands. A failed fetch offers nothing, and the field stays free text.
 */
@Injectable({ providedIn: 'root' })
export class ChatDefinitionOptions {
  private readonly service = inject(EntityDefinitionService);
  private readonly fetched = signal<readonly EntityDefinition[]>([]);
  private loading?: Promise<void>;

  /** Every entity definition's code, alphabetically. */
  entityCodes(): Selectable[] {
    this.loading ??= firstValueFrom(this.service.findAll()).then(
      (definitions) => this.fetched.set(definitions),
      () => undefined,
    );
    const codes = [...new Set(this.fetched().map((definition) => definition.code))];
    codes.sort((left, right) => left.localeCompare(right));
    return codes.map((code) => ({ key: code, value: code }));
  }
}
