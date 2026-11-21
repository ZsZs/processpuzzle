import { inject, Injectable } from '@angular/core';
import type { Observable } from 'rxjs';
import { tap } from 'rxjs';
import type { PersistedEntity } from '../base-entity/base-entity';
import { ENTITY_SERVICE_ROOT_KEY } from '../base-entity-definition/entity-definition.service';
import { BaseEntityRestService } from '../base-entity-service/base-entity-rest.service';
import { OrganizationUnitCatalog } from '../base-form/fundamental-types/organization-unit-catalog';
import type { OrganizationUnitRecord } from './organization-unit';
import { OrganizationUnitMapper } from './organization-unit.mapper';

/**
 * CRUD access to `/organizations/{orgKey}/units` — the units an organization adds, as the designer writes them. A second
 * service over the resource {@link OrganizationUnitService} reads, for the reason `EntityDefinitionAuthoringService` is one:
 * this is the store's repository, with paged reads and errors that reach the screen.
 *
 * A saved unit changes the catalog every control and rule reads, so a successful create or update reloads it at once: the
 * live `UNIT_CATALOG` then answers with the unit, in the controls built after that. There is no delete — the contract has none,
 * because a stored value may carry the unit; a unit is retired by its status instead.
 */
@Injectable({ providedIn: 'root' })
export class OrganizationUnitAuthoringService extends BaseEntityRestService<OrganizationUnitRecord> {
  private readonly catalog = inject(OrganizationUnitCatalog);

  constructor(protected override entityMapper: OrganizationUnitMapper) {
    super(entityMapper, ENTITY_SERVICE_ROOT_KEY, 'units');
  }

  override add(entity: OrganizationUnitRecord, id?: number): Observable<PersistedEntity<OrganizationUnitRecord>> {
    return super.add(entity, id).pipe(tap(() => this.reloadCatalog()));
  }

  override update(entity: PersistedEntity<OrganizationUnitRecord>): Observable<PersistedEntity<OrganizationUnitRecord>> {
    return super.update(entity).pipe(tap(() => this.reloadCatalog()));
  }

  private reloadCatalog(): void {
    this.catalog.invalidate();
    void this.catalog.ensureLoaded();
  }
}
