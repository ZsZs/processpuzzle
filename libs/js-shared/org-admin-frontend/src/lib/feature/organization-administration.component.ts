import { Component, inject } from '@angular/core';
import { CardsGridSpec, MatCardsGridComponent } from '@processpuzzle/widgets';
import { ORG_ADMIN_ORG_KEY } from '../domain/organization-user.service';

/** Transloco prefix of the overview's texts, in the org-admin scope: `org_admin.administration.<card>.<field>`. */
export const ADMINISTRATION_TRANSLOCO_PREFIX = 'org_admin.administration';

/**
 * The administration overview: one card per thing an administrator manages, on the standard cards grid.
 *
 * It is the landing page of the administration branch, so the sidenav's Administration entry opens here
 * rather than straight into the users. The links are absolute, built from the organization key, because
 * this page is reached at the branch root and has no path of its own to resolve a relative link against.
 */
@Component({
  selector: 'pp-organization-administration',
  standalone: true,
  imports: [MatCardsGridComponent],
  template: `<pp-cards-grid [cards]="cards"></pp-cards-grid>`,
})
export class OrganizationAdministrationComponent {
  private readonly orgKey = inject(ORG_ADMIN_ORG_KEY);

  readonly cards: CardsGridSpec[] = [
    {
      icon: 'group',
      title: 'users.title',
      subtitle: '',
      content: ['users.text'],
      actions: [{ caption: 'users.open', link: `/${this.orgKey}/admin/organization-user`, buttonType: 'filled' }],
      translocoPrefix: ADMINISTRATION_TRANSLOCO_PREFIX,
    },
    {
      icon: 'badge',
      title: 'roles.title',
      subtitle: '',
      content: ['roles.text'],
      actions: [{ caption: 'roles.open', link: `/${this.orgKey}/admin/organization-role`, buttonType: 'outlined' }],
      translocoPrefix: ADMINISTRATION_TRANSLOCO_PREFIX,
    },
  ];
}
