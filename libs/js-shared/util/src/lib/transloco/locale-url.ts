/**
 * The two pure halves of the locale URL prefix: taking one off an incoming URL, putting one back on
 * an outgoing one.
 *
 * They are separate from {@link LocaleUrlSerializer} because the serializer's interesting behaviour is
 * the side effect of switching the active language, and that is not what needs exhaustive test cases —
 * the string surgery is. Query strings and fragments are the reason: `/hu?page=2` and `/hu#top` carry
 * no path separator after the locale, so a naive split on `/` loses them.
 */

/** An incoming URL taken apart: the locale it named, if any, and what is left once it is removed. */
export interface LocalizedUrl {
  /** The locale segment, present only when the URL's first segment is one of the supported codes. */
  readonly locale?: string;
  /** The URL with that segment removed, always rooted at `/` so the router sees a canonical form. */
  readonly path: string;
}

/** Where the URL's first segment ends: the next `/`, `?` or `#`, or the end of the string. */
function endOfFirstSegment(url: string): number {
  const boundary = [url.indexOf('/'), url.indexOf('?'), url.indexOf('#')].filter((index) => index >= 0);
  return boundary.length === 0 ? -1 : Math.min(...boundary);
}

/**
 * Splits a locale prefix off `url`, when its first segment is one of `supportedLocales`.
 *
 * Membership in `supportedLocales` is the whole test, which is why an application must not route a
 * top-level path whose name collides with a language code — a `/de` *route* would be swallowed here and
 * never reach the router. The codes are two-letter ISO ones and the platform's own top-level paths are
 * words (`base-entity`, `design`, `ci-cd`), so the collision is avoidable rather than merely unlikely.
 *
 * Leaves the URL untouched when it names no locale, so URLs from before this feature — and the assets
 * and API paths that never carry a prefix — keep working.
 */
export function splitLocaleFromUrl(url: string, supportedLocales: readonly string[]): LocalizedUrl {
  const withoutLeadingSlash = url.startsWith('/') ? url.slice(1) : url;
  const end = endOfFirstSegment(withoutLeadingSlash);
  const candidate = end === -1 ? withoutLeadingSlash : withoutLeadingSlash.slice(0, end);
  if (!supportedLocales.includes(candidate)) return { path: url };

  const rest = end === -1 ? '' : withoutLeadingSlash.slice(end);
  return { locale: candidate, path: rest.startsWith('/') ? rest : `/${rest}` };
}

/**
 * Puts `locale` back in front of a serialized URL, inverting {@link splitLocaleFromUrl}.
 *
 * The two special cases both exist so that the round trip is exact rather than merely equivalent: the
 * root would otherwise serialize as `/hu/` and a query-only URL as `/hu/?page=2`, and while the router
 * parses both, the address bar would gain a slash on every language switch.
 */
export function withLocale(path: string, locale: string | undefined): string {
  if (!locale) return path;
  if (path === '' || path === '/') return `/${locale}`;
  if (path.startsWith('/?') || path.startsWith('/#')) return `/${locale}${path.slice(1)}`;
  return path.startsWith('/') ? `/${locale}${path}` : `/${locale}/${path}`;
}
