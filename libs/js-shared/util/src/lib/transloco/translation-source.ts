import { InjectionToken } from '@angular/core';

/**
 * Which backend serves a given transloco scope's bundles.
 *
 * Each feature stores and serves its own translations — the libraries are meant to become services with
 * separate databases — so a scope alone does not say where its bundle comes from. This maps the one to
 * the other: `scopes` are the scope names a feature owns, `serviceRootKey` names the
 * `BaseConfiguration` property holding that feature's organization-scoped root, and `segment` is the
 * feature's path segment below it.
 *
 * A feature library contributes its own entry, the way `BASE_APP_ENTITY_FACADES` and
 * `BASE_ENTITY_FACADE_REGISTRY` are already contributed, so `util` needs to know none of them.
 */
export interface TranslationSource {
  /** Scope names this feature owns. */
  readonly scopes: readonly string[];
  /** `BaseConfiguration` key of the feature's `<host>/organizations/<orgKey>` root. */
  readonly serviceRootKey: string;
  /** Path segment below that root — `app`, `entity`, `widget`, … */
  readonly segment: string;
}

/**
 * The registry itself. `multi`, so every library adds to it rather than replacing it.
 *
 * Beware the Angular rule that bit `lazyMount`: a `multi` token is *not* merged across injectors, so all
 * contributions have to be provided in one place — the application's root providers — rather than
 * scattered over route branches.
 */
export const TRANSLATION_SOURCE_REGISTRY = new InjectionToken<TranslationSource[]>('TRANSLATION_SOURCE_REGISTRY');

/**
 * Where an unregistered scope is looked up.
 *
 * base-app, because the scopes nobody registers statically are precisely the designer-authored ones: a
 * `ModuleDefinition` names its scope at run-time (`translocoScope`, defaulting to the module key), and
 * modules are base-app's aggregate. This is also the only case that *has* to reach a backend at all —
 * every static scope has an asset and never gets this far.
 */
export const DEFAULT_TRANSLATION_SOURCE: TranslationSource = { scopes: [], serviceRootKey: 'APP_SERVICE_ROOT', segment: 'app' };

/** The source that owns `scope`, or {@link DEFAULT_TRANSLATION_SOURCE} when none claims it. */
export function translationSourceOf(registry: readonly TranslationSource[] | null, scope: string | undefined): TranslationSource {
  if (!scope) return DEFAULT_TRANSLATION_SOURCE;
  return (registry ?? []).find((source) => source.scopes.includes(scope)) ?? DEFAULT_TRANSLATION_SOURCE;
}
