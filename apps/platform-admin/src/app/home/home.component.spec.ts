import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter, RouterLink } from '@angular/router';
import { setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';
import { HomeComponent } from './home.component';

describe('HomeComponent', () => {
  const testConfig: TranslocoTestConfig = {
    translations: {
      en: {
        home: {
          title: 'Platform administration',
          subtitle: 'Tenants, their identity realms and what they are billed.',
          organizations: { title: 'Organizations', text: 'Create a tenant.' },
          plans: { title: 'Plans', text: 'The plans on offer.' },
          subscriptions: { title: 'Subscriptions', text: 'Which plan each tenant is on.' },
          invoices: { title: 'Invoices', text: 'What has been billed.' },
        },
      },
    },
  };
  let component: HomeComponent;
  let fixture: ComponentFixture<HomeComponent>;

  beforeEach(async () => {
    const result = await setUpTranslocoTestBed(HomeComponent, testConfig, { providers: [provideRouter([])] });
    component = result.component;
    fixture = result.fixture;
  });

  it('Should create component', () => {
    expect(component).toBeTruthy();
  });

  it('renders one card per section, each linking to its screen', () => {
    const cards = fixture.debugElement.queryAll(By.css('mat-card'));
    expect(cards.length).toBe(component.sections.length);

    for (const section of component.sections) {
      const card = fixture.debugElement.query(By.css(`[test-id="home-card-${section.key}"]`));
      expect(card, `no card for '${section.key}'`).toBeTruthy();
      // A `mat-card` is a div, so `RouterLink` sets no `href` to read — the destination only exists as
      // the directive's own url tree.
      expect(card.injector.get(RouterLink).urlTree?.toString()).toBe(section.path);
    }
  });

  it('the card paths are the routes platform-admin mounts', () => {
    expect(component.sections.map((section) => section.path)).toEqual(['/organization', '/plan', '/subscription', '/invoice']);
  });

  it('renders the translated title and section text', async () => {
    await fixture.whenStable();
    expect((fixture.debugElement.query(By.css('h1')).nativeElement as HTMLElement).textContent?.trim()).toBe('Platform administration');
    expect((fixture.debugElement.query(By.css('[test-id="home-card-plans"] mat-card-content')).nativeElement as HTMLElement).textContent?.trim()).toBe('The plans on offer.');
  });
});
