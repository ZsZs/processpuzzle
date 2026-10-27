import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { provideOrgAdminTesting, required } from '../domain/test-organization-user';
import { OrganizationAdministrationComponent } from './organization-administration.component';

describe('OrganizationAdministrationComponent', () => {
  function render(): HTMLElement {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ imports: [OrganizationAdministrationComponent], providers: [...provideOrgAdminTesting(), provideRouter([])] });
    const fixture = TestBed.createComponent(OrganizationAdministrationComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('offers the users and the roles, each as a card that opens its screen', () => {
    const host = render();

    expect(required(host, '#administration-users').textContent).toContain('assign their roles');
    expect(required<HTMLAnchorElement>(host, '#administration-users a').getAttribute('href')).toBe('/my-org/admin/organization-user');
    expect(required(host, '#administration-roles').textContent).toContain('read-only');
    expect(required<HTMLAnchorElement>(host, '#administration-roles a').getAttribute('href')).toBe('/my-org/admin/organization-role');
  });
});
