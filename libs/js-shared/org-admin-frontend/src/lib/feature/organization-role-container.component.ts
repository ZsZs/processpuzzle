import { Component, inject } from '@angular/core';
import { BaseEntityContainerComponent } from '@processpuzzle/base-entity';
import { createOrganizationRoleDescriptor } from '../domain/organization-role.descriptors';
import { OrganizationRoleStore } from '../domain/organization-role.store';

/**
 * The role screens: the catalog of the organization's own roles, with create, describe and delete.
 *
 * Nothing is added beside the generic actions. Who may change a role is a per-row rule of the descriptor,
 * so a platform role or a workflow role shows read-only without any code here.
 */
@Component({
  selector: 'pp-organization-role-container',
  standalone: true,
  imports: [BaseEntityContainerComponent],
  template: `<base-entity-container [entityDescriptor]="baseEntityDescriptor"></base-entity-container>`,
})
export class OrganizationRoleContainerComponent {
  readonly baseEntityDescriptor = createOrganizationRoleDescriptor();

  constructor() {
    this.baseEntityDescriptor.store = inject(OrganizationRoleStore);
  }
}
