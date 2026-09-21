import { beforeEach, describe, expect, it } from 'vitest';
import { ApplicationInitStatus, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { provideRouter, Router, UrlSerializer } from '@angular/router';
import { AvailableLangs, TranslocoService } from '@jsverse/transloco';
import { provideLocaleRouting } from './provide-locale-routing';
import { LocaleUrlSerializer } from './locale-url-serializer';
import { BehaviorSubject } from 'rxjs';

@Component({ template: '' })
class BlankComponent {}

/** `langChanges$` replays, as transloco's does — the emission on subscribe is what the initializer must ignore. */
class FakeTranslocoService {
  private readonly lang$ = new BehaviorSubject<string>('en');
  readonly langChanges$ = this.lang$.asObservable();

  getActiveLang(): string {
    return this.lang$.value;
  }

  setActiveLang(lang: string): void {
    this.lang$.next(lang);
  }

  getAvailableLangs(): AvailableLangs {
    return ['en', 'de', 'fr', 'hu', 'es'];
  }
}

describe('provideLocaleRouting', () => {
  let router: Router;
  let location: Location;
  let transloco: FakeTranslocoService;

  beforeEach(() => {
    transloco = new FakeTranslocoService();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'home', component: BlankComponent },
          { path: 'orders', component: BlankComponent },
          // The application's own root redirect, reproduced because the bare origin is the URL most
          // visitors arrive on and it is the one case where the locale is applied to a URL the user
          // never typed.
          { path: '', pathMatch: 'full', redirectTo: 'home' },
        ]),
        provideLocationMocks(),
        { provide: TranslocoService, useValue: transloco },
        // After `provideRouter`, which binds `UrlSerializer` to `DefaultUrlSerializer`.
        provideLocaleRouting(),
      ],
    });

    // Runs the app initializer that installs the language → address bar subscription.
    TestBed.inject(ApplicationInitStatus);
    router = TestBed.inject(Router);
    location = TestBed.inject(Location);
  });

  it('overrides the router default with the locale-aware serializer.', () => {
    expect(TestBed.inject(UrlSerializer)).toBeInstanceOf(LocaleUrlSerializer);
  });

  it('writes the active language into the address bar on navigation.', async () => {
    await router.navigateByUrl('/home');

    expect(location.path()).toBe('/en/home');
  });

  it('sends the bare origin to the default language, so `localhost:4200/` lands on `/en/home`.', async () => {
    // What a visitor typing the host with no path gets. The redirect resolves to `/home` and the
    // serializer prefixes the active language on the way to the address bar, so the origin has one
    // canonical landing URL rather than a prefix-free `/home` that only gains `/en` on the next click.
    await router.navigateByUrl('/');

    expect(location.path()).toBe('/en/home');
  });

  it('applies the locale to the bare origin without stranding the user on a history entry.', async () => {
    await router.navigateByUrl('/orders');
    await router.navigateByUrl('/');

    // `/` is gone from history rather than sitting behind `/en/home`: Angular replaces the URL when a
    // route redirects, so Back reaches the previous page instead of `/`, which would only redirect
    // forward again and trap the user.
    await location.back();

    expect(location.path()).toBe('/en/orders');
  });

  it('resolves a deep link that names a language, and keeps its prefix.', async () => {
    await router.navigateByUrl('/hu/orders');

    expect(transloco.getActiveLang()).toBe('hu');
    expect(location.path()).toBe('/hu/orders');
  });

  it('rewrites the address bar when the language changes outside a navigation.', async () => {
    await router.navigateByUrl('/home');

    transloco.setActiveLang('de');

    expect(location.path()).toBe('/de/home');
  });

  it('does not touch the address bar before the first navigation settles, so a deep link survives.', () => {
    // The subscription's replayed first emission has already happened by now, during the initializer.
    expect(location.path()).toBe('');
  });

  it('adds no history entry for a language switch, so Back leaves the page rather than the language.', async () => {
    await router.navigateByUrl('/home');
    await router.navigateByUrl('/orders');

    transloco.setActiveLang('fr');
    expect(location.path()).toBe('/fr/orders');

    await location.back();

    // `/home`, not `/orders` — the switch replaced the current entry instead of pushing one. And the
    // restored entry carries the language it was visited in, which `LocaleUrlSerializer.parse` then
    // makes active again: going back undoes the language switch along with the navigation, which is
    // what a history entry means.
    expect(location.path()).toBe('/en/home');
  });
});
