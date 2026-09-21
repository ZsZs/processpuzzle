import { DestroyRef, EnvironmentProviders, inject, makeEnvironmentProviders, provideAppInitializer } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Location } from '@angular/common';
import { Event, NavigationCancel, NavigationEnd, NavigationError, NavigationStart, Router, UrlSerializer } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { LocaleUrlSerializer } from './locale-url-serializer';

/**
 * Puts the active language in the URL: `/hu/base-entity` rather than `/base-entity`.
 *
 * Two directions, and they need different machinery. **URL → language** is
 * {@link LocaleUrlSerializer}, which switches the active language whenever it parses a URL naming
 * another one. **Language → URL** is the subscription below, because a language switch is not a
 * navigation — `LanguageSelectorListComponent` calls `setActiveLang` and nothing asks the router for a
 * new address. The current route is unchanged and only its *rendering* differs, so a `replaceState`
 * rather than a navigation is also the honest description of what happened: no history entry, and the
 * back button still goes where the user came from.
 *
 * **Order matters where this is used.** `provideRouter` binds `UrlSerializer` to
 * `DefaultUrlSerializer`, so this must come *after* it in the providers array, or the default wins and
 * the whole feature silently does nothing — URLs would carry no prefix and a `/hu/...` deep link would
 * 404 into the index.html fallback.
 *
 * ```typescript
 * providers: [
 *   provideRouter(appRoutes, withComponentInputBinding()),
 *   provideTranslocoService(runtimeConfiguration.LANGUAGE_CONFIGURATION),
 *   provideLocaleRouting(), // after provideRouter — it overrides UrlSerializer
 * ];
 * ```
 */
export function provideLocaleRouting(): EnvironmentProviders {
  return makeEnvironmentProviders([{ provide: UrlSerializer, useClass: LocaleUrlSerializer }, provideAppInitializer(() => writeLocaleToAddressBar())]);
}

/**
 * Rewrites the address bar when the language changes, and only then.
 *
 * `router.url` is a getter that re-serializes the current tree on each access, so by the time
 * `langChanges$` fires it already reads `/de/...`; there is nothing to compute here beyond deciding
 * *whether* to write it.
 *
 * That decision is the substance. Two emissions must not be written, and both are ones the naive
 * version gets wrong:
 *
 * - **Before the first navigation settles.** `langChanges$` is a replaying subject, so subscribing
 *   emits the default language immediately — at which point `router.url` is still `/` and writing it
 *   would discard the deep link the user arrived on, before the router ever read it.
 * - **During a navigation.** `LocaleUrlSerializer.parse` switches the language at the *start* of a
 *   navigation to a URL naming another one, so this fires while `currentUrlTree` is still the previous
 *   route. Writing then would show the old page's path under the new language for a few frames. There
 *   is also nothing to do: the router writes the address bar itself when the navigation completes, with
 *   the new language, because it serializes through {@link LocaleUrlSerializer}.
 *
 * Which leaves exactly one case — an idle application whose language changed because the user picked
 * one.
 */
function writeLocaleToAddressBar(): void {
  const router = inject(Router);
  const location = inject(Location);
  const translocoService = inject(TranslocoService);
  const destroyRef = inject(DestroyRef);

  let navigating = false;
  let firstNavigationSettled = false;

  router.events.pipe(takeUntilDestroyed(destroyRef)).subscribe((event: Event) => {
    if (event instanceof NavigationStart) {
      navigating = true;
    } else if (event instanceof NavigationEnd || event instanceof NavigationCancel || event instanceof NavigationError) {
      navigating = false;
      firstNavigationSettled = true;
    }
  });

  translocoService.langChanges$.pipe(takeUntilDestroyed(destroyRef)).subscribe(() => {
    if (navigating || !firstNavigationSettled) return;
    location.replaceState(router.url);
  });
}
