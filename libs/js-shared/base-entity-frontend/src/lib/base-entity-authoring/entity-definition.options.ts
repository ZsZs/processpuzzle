import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { type Selectable } from '../base-entity/base-entity-attr.descriptor';
import { EntityDefinition } from '../base-entity-definition/entity-definition';
import { EntityDefinitionService } from '../base-entity-definition/entity-definition.service';
import { EntityDefinitionStore } from './entity-definition.store';

/**
 * The organization's entity types as options for the designer's pickers, by **code** — where a definition names
 * another definition: an attribute's linked entity, a definition's component parents.
 *
 * Two sources, merged. A fetch of every definition — whatever its status, since a designer links to a draft
 * as readily as to a live one — and the rows of the designer's own `EntityDefinitionStore`, which hold a
 * definition the moment it is saved. The store alone would offer only the page its list shows; the fetch alone
 * would miss what was created after it.
 *
 * `codes()` is read from a dropdown's template on every change detection, so it is synchronous: the first call
 * starts the fetch, and the options grow when it lands. A failed fetch leaves the store's rows on offer — an
 * organization with no definitions yet answers the list with an error, and that is not something to report.
 */
@Injectable({ providedIn: 'root' })
export class EntityDefinitionOptions {
  private readonly service = inject(EntityDefinitionService);
  private readonly store = inject(EntityDefinitionStore);
  private readonly fetched = signal<readonly EntityDefinition[]>([]);
  private loading?: Promise<void>;

  /** Every definition's code, alphabetically. */
  codes(): Selectable[] {
    return options(this.definitions().map((definition) => definition.code));
  }

  private definitions(): EntityDefinition[] {
    this.loading ??= firstValueFrom(this.service.findAll()).then(
      (definitions) => this.fetched.set(definitions),
      () => undefined,
    );
    return [...this.fetched(), ...this.store.entities()];
  }
}

function options(values: Array<string | undefined>): Selectable[] {
  const unique = [...new Set(values.filter((value): value is string => !!value))];
  return unique.sort((left, right) => left.localeCompare(right)).map((value) => ({ key: value, value }));
}
