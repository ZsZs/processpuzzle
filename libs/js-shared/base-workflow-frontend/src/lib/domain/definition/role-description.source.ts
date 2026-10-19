import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import type { BaseEntityLoadResponse } from '@processpuzzle/base-entity';
import { RoleDefinition } from './role-definition';
import { RoleDefinitionService } from './role-definition.service';

/**
 * The tenant's role catalog read as descriptions of realm roles: a role definition's id is the name of
 * the realm role `SyncRoleDirectory` projects it to, so it is also what the user's token carries.
 *
 * Shaped to auth's `RoleDescriptionSource` without importing it — base-workflow does not depend on
 * auth — and bound to `ROLE_DESCRIPTION_SOURCE` by the application, which knows both.
 */
@Injectable({ providedIn: 'root' })
export class WorkflowRoleDescriptionSource {
  private readonly service = inject(RoleDefinitionService);

  describeRoles(): Observable<ReadonlyMap<string, { name?: string; description?: string }>> {
    return this.service.findAll().pipe(map((response) => new Map(asList(response).map((role) => [role.id, { name: role.name, description: role.description }]))));
  }
}

/** `findAll` answers with a list, a page or — for a one-row collection — the row itself. */
function asList(response: BaseEntityLoadResponse<RoleDefinition> | RoleDefinition[] | RoleDefinition): RoleDefinition[] {
  if (Array.isArray(response)) return response;
  if ('content' in response) return response.content;
  return [response];
}
