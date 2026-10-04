import { Component } from '@angular/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, RouterOutlet } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideHttpClient } from '@angular/common/http';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { APP_CONCEPTS } from '@processpuzzle/base-app';
import { appRoutes } from '../../app.routes';
import { SamplesComponent } from './samples.component';

@Component({ imports: [RouterOutlet], template: '<router-outlet />' })
class EntityContainerStubComponent {}

@Component({ template: '<div data-testid="entity-view">Entity view</div>' })
class EntityViewStubComponent {}

describe('base-apps SamplesComponent', () => {
  beforeEach(() => {
    const samplesRoute = appRoutes.find((route) => route.path === 'base-app')?.children?.find((route) => route.path === 'samples');
    if (!samplesRoute?.children) throw new Error('Base-app samples route is missing');

    TestBed.configureTestingModule({
      imports: [
        TranslocoTestingModule.forRoot({
          langs: {
            en: {
              'base-apps': { samples_desc_1: 'Application samples', samples_desc_2: 'Generated table and form' },
              base_app: {
                concepts: {
                  title: 'How a dynamic application is put together',
                  intro: 'Application concepts',
                  ...Object.fromEntries(APP_CONCEPTS.map((concept) => [concept, { title: concept, body: concept }])),
                },
              },
            },
          },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
          preloadLangs: true,
        }),
      ],
      providers: [
        provideHttpClient(),
        provideRouter([
          {
            path: 'en/base-app/samples',
            component: SamplesComponent,
            children: samplesRoute.children.map((route) =>
              route.path === 'app-definition'
                ? {
                    ...route,
                    component: EntityContainerStubComponent,
                    providers: [],
                    children: [
                      { path: '', pathMatch: 'full', redirectTo: 'list' },
                      { path: 'list', component: EntityViewStubComponent },
                      { path: ':id/details', component: EntityViewStubComponent },
                      { path: ':id/preview', component: EntityViewStubComponent },
                    ],
                  }
                : route,
            ),
          },
        ]),
      ],
    });
  });

  it('opens the entity content without an App Definition toggle', async () => {
    const harness = await RouterTestingHarness.create('/en/base-app/samples');

    expect(TestBed.inject(Router).url).toBe('/en/base-app/samples/app-definition/list');
    expect(harness.routeNativeElement?.querySelector('[data-testid="entity-view"]')).not.toBeNull();
    expect(harness.routeNativeElement?.querySelector('mat-button-toggle-group')).toBeNull();
    expect(harness.routeNativeElement?.querySelector('#app-concepts-title')?.textContent).toBe('How a dynamic application is put together');
  });

  it('keeps the page-level guide outside the entity across list, details and preview navigation', async () => {
    const harness = await RouterTestingHarness.create('/en/base-app/samples');
    const guide = harness.routeNativeElement?.querySelector('pp-app-concepts-guide');

    for (const path of ['demo/details', 'demo/preview', 'list']) {
      await harness.navigateByUrl(`/en/base-app/samples/app-definition/${path}`);

      expect(harness.routeNativeElement?.querySelector('[data-testid="entity-view"]')).not.toBeNull();
      expect(harness.routeNativeElement?.querySelectorAll('pp-app-concepts-guide')).toHaveLength(1);
      expect(harness.routeNativeElement?.querySelector('pp-app-concepts-guide')).toBe(guide);
      expect(guide?.parentElement?.tagName.toLowerCase()).toBe('base-apps-samples');
    }
  });
});
