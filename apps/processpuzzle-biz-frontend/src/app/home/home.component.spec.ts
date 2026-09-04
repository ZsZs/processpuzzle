import { describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter, RouterLink } from '@angular/router';
import { ORG_ADMIN_ORG_KEY } from '@processpuzzle/org-admin';
import { setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';
import { HomeComponent } from './home.component';

describe('HomeComponent', () => {
  const testConfig: TranslocoTestConfig = {
    translations: {
      en: {
        home: {
          title: 'ProcessPuzzle',
          subtitle: 'Design your application.',
          noTenant: 'Open your organization at /<your-organization-key>/admin.',
          users: { title: 'Users', text: 'Invite people into your organization.' },
        },
      },
    },
  };

  const renderFor = async (orgKey?: string): Promise<{ component: HomeComponent; fixture: ComponentFixture<HomeComponent> }> => {
    TestBed.resetTestingModule();
    return setUpTranslocoTestBed(HomeComponent, testConfig, {
      providers: [provideRouter([]), ...(orgKey === undefined ? [] : [{ provide: ORG_ADMIN_ORG_KEY, useValue: orgKey }])],
    });
  };

  it('Should create component', async () => {
    const { component } = await renderFor('acme');
    expect(component).toBeTruthy();
  });

  it('links the users card into the tenant admin branch', async () => {
    const { component, fixture } = await renderFor('acme');
    expect(component.sections.map((section) => section.link)).toEqual(['/acme/admin/organization-user']);

    const card = fixture.debugElement.query(By.css('[test-id="home-card-users"]'));
    expect(card).toBeTruthy();
    // A `mat-card` is a div, so `RouterLink` sets no `href` to read — the destination only exists as
    // the directive's own url tree.
    expect(card.injector.get(RouterLink).urlTree?.toString()).toBe('/acme/admin/organization-user');
  });

  it('renders the translated title and card text', async () => {
    const { fixture } = await renderFor('acme');
    await fixture.whenStable();
    expect((fixture.debugElement.query(By.css('h1')).nativeElement as HTMLElement).textContent?.trim()).toBe('ProcessPuzzle');
    expect((fixture.debugElement.query(By.css('[test-id="home-card-users"] mat-card-content')).nativeElement as HTMLElement).textContent?.trim()).toBe(
      'Invite people into your organization.',
    );
  });

  it('offers no card and says which URL to use when the URL named no tenant', async () => {
    const { component, fixture } = await renderFor('');
    await fixture.whenStable();

    expect(component.sections).toEqual([]);
    expect(fixture.debugElement.queryAll(By.css('mat-card'))).toEqual([]);
    expect((fixture.debugElement.query(By.css('[test-id="home-no-tenant"]')).nativeElement as HTMLElement).textContent?.trim()).toBe(
      'Open your organization at /<your-organization-key>/admin.',
    );
  });

  it('survives the token being absent altogether', async () => {
    const { component } = await renderFor(undefined);
    expect(component.sections).toEqual([]);
  });
});
