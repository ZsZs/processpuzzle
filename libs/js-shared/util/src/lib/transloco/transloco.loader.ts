import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Translation, TranslocoLoader, TranslocoLoaderData } from '@jsverse/transloco';
import { catchError, map, Observable, of } from 'rxjs';
import { RUNTIME_CONFIGURATION } from '../runtime-configuration/configuration.injection-tokens';
import { serviceRootOf } from '../runtime-configuration/service-root';
import { TRANSLATION_SOURCE_REGISTRY, translationSourceOf } from './translation-source';

/**
 * Loads a transloco bundle from the application's assets, falling back to the backend when there is no
 * asset to load.
 *
 * The fallback is not a nicety. A designer-authored module names its transloco scope at run-time — see
 * `ModuleDefinition.translocoScope` and `app-route-builder`'s `lazyMount` — so no application build can
 * have shipped `assets/i18n/<scope>/<lang>.json` for it. Those bundles have nowhere to come from but the
 * feature that stores them.
 *
 * Assets stay first because they are the common case: fourteen scopes ship as files and would otherwise
 * pay a round trip each. Only a miss reaches the backend.
 */
@Injectable({ providedIn: 'root' })
export class TranslocoHttpLoader implements TranslocoLoader {
  private readonly http = inject(HttpClient);
  private readonly runtimeConfiguration = inject(RUNTIME_CONFIGURATION, { optional: true });
  private readonly sources = inject(TRANSLATION_SOURCE_REGISTRY, { optional: true });

  /**
   * `path` is transloco's composite: `en` for the root bundle, `base_app/en` for a scoped one. `data`
   * carries the scope separately, which is what this reads — splitting the path would guess wrong about
   * a scope containing a slash.
   */
  getTranslation(path: string, data?: TranslocoLoaderData): Observable<Translation> {
    const scope = data?.scope;
    const locale = scope && path.startsWith(`${scope}/`) ? path.slice(scope.length + 1) : path;

    return this.http.get<Translation>(`assets/i18n/${path}.json`).pipe(
      map((asset) => this.requireBundle(asset)),
      catchError(() => this.fromBackend(scope, locale)),
    );
  }

  /**
   * Rejects anything that is not a JSON object, so the caller falls back.
   *
   * The three deployments disagree about what a missing asset looks like. The Angular dev server 404s for
   * a `.json` URL, but nginx (`tools/docker/processpuzzle-testbed-frontend/nginx.conf`) and Firebase Hosting (`firebase.json`)
   * rewrite unknown paths to `index.html` and answer **200**. HttpClient turns that into an error by
   * itself, because the HTML fails `JSON.parse` — but an *empty* 200 body parses to `null` and would sail
   * through. Hence a positive check rather than trusting the status.
   */
  private requireBundle(candidate: Translation | null): Translation {
    if (!candidate || typeof candidate !== 'object' || Array.isArray(candidate)) {
      throw new Error('not a translation bundle');
    }
    return candidate;
  }

  /**
   * The owning feature's translations resource, or an empty bundle.
   *
   * **Resolving empty rather than erroring is load-bearing.** With `fallbackLang: ['en']` and `en`
   * active, transloco treats a failed load of the fallback language as fatal and throws
   * `TranslationLoadError`, which propagates through the pipe's `forkJoin` and takes the component tree
   * down. It also sidesteps `failedRetries` (default 2), which would otherwise replay the whole
   * asset-then-backend chain three times per bundle.
   *
   * A missing backend is therefore indistinguishable here from an untranslated scope, by design — the
   * testbed is meant to run with no ProcessPuzzle backend at all. One case worth knowing when debugging:
   * a CORS misconfiguration surfaces as HTTP status 0 and lands in exactly this branch, so "no backend
   * translations" and "the origin is not allow-listed" look the same from the outside.
   */
  private fromBackend(scope: string | undefined, locale: string): Observable<Translation> {
    const source = translationSourceOf(this.sources, scope);
    const root = serviceRootOf(this.runtimeConfiguration, source.serviceRootKey);
    if (!root) return of({});

    const suffix = scope ? `${scope}/${locale}` : locale;
    return this.http.get<Translation>(`${root}/${source.segment}/translations/${suffix}`).pipe(
      map((bundle) => this.requireBundle(bundle)),
      catchError(() => of({})),
    );
  }
}
