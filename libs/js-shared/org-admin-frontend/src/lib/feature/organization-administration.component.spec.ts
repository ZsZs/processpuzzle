import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { provideOrgAdminTesting } from '../domain/test-organization-user';
import { ADMINISTRATION_TRANSLOCO_PREFIX, OrganizationAdministrationComponent } from './organization-administration.component';

describe('OrganizationAdministrationComponent', () => {
  function create() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ imports: [OrganizationAdministrationComponent], providers: [...provideOrgAdminTesting(), provideRouter([])] });
    return TestBed.createComponent(OrganizationAdministrationComponent).componentInstance;
  }

  it('offers the users, the roles and the AI subscription, each on a card with its icon and a button that opens it', () => {
    const { cards } = create();

    expect(cards.map((card) => [card.title, card.icon, card.actions[0].link])).toEqual([
      ['users.title', 'group', '/my-org/admin/organization-user'],
      ['roles.title', 'badge', '/my-org/admin/organization-role'],
      ['ai_subscription.title', 'smart_toy', '/my-org/admin/ai-subscription'],
    ]);
  });

  it('translates every card under the overview prefix', () => {
    const { cards } = create();

    expect(cards.every((card) => card.translocoPrefix === ADMINISTRATION_TRANSLOCO_PREFIX)).toBe(true);
  });
});
