import { AppTranslationsLoader } from './app-translations.loader';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, Routes } from '@angular/router';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppDefinition, RouteDefinition } from '../../domain/app-definition';
import { AppDefinitionService } from '../../domain/app-definition.service';
import { AppDefinitionStore } from '../../domain/app-definition.store';
import { ModuleDefinitionService } from '../../domain/module-definition.service';
import { ModuleDefinitionStore } from '../../domain/module-definition.store';
import { PublishedAppStore, publishedAppRoutesGuard } from './published-app-routes';

@Component({ template: '' })
class StubComponent {}

function published(...paths: string[]): AppDefinition {
  return new AppDefinition({ id: 'app', name: 'Acme', routes: paths.map((path) => new RouteDefinition({ path, title: path, kind: 'WIDGETS' })) });
}

describe('publishedAppRoutesGuard', () => {
  const findPublished = vi.fn();
  const fallback: Routes = [
    { path: 'home', component: StubComponent },
    { path: '', pathMatch: 'full', redirectTo: 'home' },
  ];

  beforeEach(() => {
    findPublished.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'acme/design', component: StubComponent },
          { path: 'acme', component: StubComponent, canMatch: [publishedAppRoutesGuard('app', fallback)] },
          { path: '**', redirectTo: 'acme' },
        ]),
        provideTranslocoTesting({ translations: {} }),
        { provide: AppDefinitionService, useValue: { findPublished } },
        { provide: AppDefinitionStore, useValue: { loadById: vi.fn() } },
        { provide: ModuleDefinitionStore, useValue: { loadById: vi.fn() } },
        { provide: ModuleDefinitionService, useValue: { findById: vi.fn() } },
        { provide: AppTranslationsLoader, useValue: { activate: () => Promise.resolve() } },
      ],
    });
  });

  it("lands on the published application's first route", async () => {
    findPublished.mockReturnValue(of(published('orders', 'customers')));
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/acme');

    expect(router.url).toBe('/acme/orders');
    expect(TestBed.inject(PublishedAppStore).published()?.name).toBe('Acme');
  });

  it('renders the fallback while nothing has been published', async () => {
    findPublished.mockReturnValue(of(undefined));
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/acme');

    expect(router.url).toBe('/acme/home');
    expect(TestBed.inject(PublishedAppStore).loaded()).toBe(true);
  });

  it('renders the fallback when the published read fails', async () => {
    findPublished.mockReturnValue(throwError(() => new Error('down')));
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/acme');

    expect(router.url).toBe('/acme/home');
  });

  it('renders the fallback for a published revision without routes, rather than never settling', async () => {
    findPublished.mockReturnValue(of(published()));
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/acme');

    expect(router.url).toBe('/acme/home');
  });

  it('does not refetch for navigations inside the application', async () => {
    findPublished.mockReturnValue(of(published('orders', 'customers')));
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/acme/orders');
    await router.navigateByUrl('/acme/customers');

    expect(router.url).toBe('/acme/customers');
    expect(findPublished).toHaveBeenCalledTimes(1);
  });

  it('refetches on coming back from the designer, so a newly published revision shows', async () => {
    findPublished.mockReturnValue(of(published('orders')));
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/acme');
    await router.navigateByUrl('/acme/design');

    findPublished.mockReturnValue(of(published('orders', 'invoices')));
    await router.navigateByUrl('/acme/invoices');

    expect(router.url).toBe('/acme/invoices');
    expect(findPublished).toHaveBeenCalledTimes(2);
  });

  it('asks a resolver for the id, and renders the fallback when it finds no application', async () => {
    TestBed.resetTestingModule();
    const resolver = vi.fn().mockResolvedValueOnce('sailrc').mockResolvedValueOnce(undefined);
    findPublished.mockReturnValue(of(published('races')));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'acme/design', component: StubComponent },
          { path: 'acme', component: StubComponent, canMatch: [publishedAppRoutesGuard(resolver, fallback)] },
          { path: '**', redirectTo: 'acme' },
        ]),
        provideTranslocoTesting({ translations: {} }),
        { provide: AppDefinitionService, useValue: { findPublished } },
        { provide: AppDefinitionStore, useValue: { loadById: vi.fn() } },
        { provide: ModuleDefinitionStore, useValue: { loadById: vi.fn() } },
        { provide: ModuleDefinitionService, useValue: { findById: vi.fn() } },
        { provide: AppTranslationsLoader, useValue: { activate: () => Promise.resolve() } },
      ],
    });
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/acme');
    expect(router.url).toBe('/acme/races');
    expect(findPublished).toHaveBeenCalledWith('sailrc');

    await router.navigateByUrl('/acme/design');
    await router.navigateByUrl('/acme');
    expect(router.url).toBe('/acme/home');
    expect(TestBed.inject(PublishedAppStore).published()).toBeUndefined();
  });
});
