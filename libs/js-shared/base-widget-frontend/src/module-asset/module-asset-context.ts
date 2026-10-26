import { DestroyRef, effect, inject, InjectionToken, Signal, signal } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * Prefix of a widget property that names an asset of the module the widget is rendered in, as opposed to a
 * URL: `module:docs/welcome.md`. Spelled out rather than inferred from "looks relative", because a relative
 * URL such as `assets/demo/welcome.md` already means something — a file of the hosting application.
 */
export const MODULE_ASSET_SCHEME = 'module:';

/**
 * What a widget needs to show a file its module brought along. Provided by the module's routes (base-app),
 * so a widget placed anywhere under a mount finds it and a widget rendered outside any module finds none —
 * in which case a `module:` value simply cannot be resolved.
 *
 * Content comes as a `Blob` rather than as a URL, because an `<img>` or a plain `fetch` cannot carry the
 * bearer token the asset endpoint requires; the caller makes an object URL of it.
 */
export interface ModuleAssetContext {
  blob(path: string): Observable<Blob>;
}

export const MODULE_ASSET_CONTEXT = new InjectionToken<ModuleAssetContext>('MODULE_ASSET_CONTEXT');

/** The module path a property value names, or `undefined` when it is an ordinary URL. */
export function moduleAssetPathOf(src: string | undefined | null): string | undefined {
  return src?.startsWith(MODULE_ASSET_SCHEME) ? src.slice(MODULE_ASSET_SCHEME.length).replace(/^\/+/, '') : undefined;
}

const HAS_SCHEME = /^[a-z][a-z0-9+.-]*:/i;

/**
 * The asset a link inside a module's markdown file points at, as a module path — or `undefined` for a link that
 * is not into the module: one with a scheme, a protocol-relative or absolute one, an in-page anchor, or a relative
 * one that climbs out above the module root.
 */
export function resolveAssetLink(fromPath: string, link: string | null | undefined): string | undefined {
  if (!link || HAS_SCHEME.test(link) || link.startsWith('//') || link.startsWith('/') || link.startsWith('#')) return undefined;
  const target = link.split(/[?#]/, 1)[0];
  let decoded: string;
  try {
    decoded = decodeURIComponent(target);
  } catch {
    return undefined;
  }
  const segments = fromPath.split('/').slice(0, -1);
  for (const segment of decoded.split('/')) {
    if (segment === '' || segment === '.') continue;
    if (segment === '..') {
      if (segments.length === 0) return undefined;
      segments.pop();
    } else {
      segments.push(segment);
    }
  }
  return segments.length ? segments.join('/') : undefined;
}

/**
 * A URL a widget can put in an `<img>`: `src` itself when it is an ordinary URL, and for a `module:` value an
 * object URL of the asset, fetched with the caller's credentials. Revoked when the value changes and when the
 * widget goes. `undefined` until the asset has arrived, and for one that cannot be had.
 *
 * Call it in an injection context, as the field initializer of a component.
 */
export function moduleAssetUrl(src: () => string | undefined): Signal<string | undefined> {
  const context = inject(MODULE_ASSET_CONTEXT, { optional: true });
  const url = signal<string | undefined>(undefined);
  let objectUrl: string | undefined;
  const revoke = () => {
    if (objectUrl) URL.revokeObjectURL(objectUrl);
    objectUrl = undefined;
  };

  effect((onCleanup) => {
    const value = src();
    const path = moduleAssetPathOf(value);
    if (path === undefined) {
      url.set(value);
      return;
    }
    url.set(undefined);
    if (!context) return;
    const subscription = context.blob(path).subscribe({
      next: (blob) => {
        revoke();
        objectUrl = URL.createObjectURL(blob);
        url.set(objectUrl);
      },
      error: () => url.set(undefined),
    });
    onCleanup(() => subscription.unsubscribe());
  });
  inject(DestroyRef).onDestroy(revoke);
  return url.asReadonly();
}
