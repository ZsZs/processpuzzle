/**
 * The org-scoped service root a feature reads its endpoints from, by `BaseConfiguration` key.
 *
 * `RUNTIME_CONFIGURATION` is typed as a bare `object`, so the lookup is reflective — the same way
 * `ObjectStoreService` reads its own root. Falls back to `APP_SERVICE_ROOT`, which is what
 * {@link BaseConfiguration.ENTITY_SERVICE_ROOT} and its siblings document: the per-feature roots are
 * optional because one host serves every feature today, and they exist so that a feature can move to a
 * host of its own without every caller changing.
 *
 * Shared rather than duplicated because the fallback is a rule of the configuration contract, not of any
 * one caller: `TranslocoHttpLoader` reads a translations resource with it and `BaseEntityRestService` an
 * entity collection, and a second copy would be free to disagree with the first.
 *
 * Returns `''` when there is no configuration at all, so a caller can decide whether an absent root is a
 * reason to skip the request — which both callers do rather than issuing one against a relative URL.
 */
export function serviceRootOf(runtimeConfiguration: object | null | undefined, key: string): string {
  if (!runtimeConfiguration) return '';
  const baseConfiguration = Reflect.get(runtimeConfiguration, 'BASE_CONFIGURATION');
  if (!baseConfiguration) return '';
  return Reflect.get(baseConfiguration, key) ?? Reflect.get(baseConfiguration, 'APP_SERVICE_ROOT') ?? '';
}
