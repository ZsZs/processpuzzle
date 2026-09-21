import { inject, Injectable } from '@angular/core';
import { DefaultUrlSerializer, UrlTree } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { splitLocaleFromUrl, withLocale } from './locale-url';

/**
 * Makes the active language part of every URL — `/hu/base-entity` rather than `/base-entity` — without
 * any route declaring it.
 *
 * This is the application half of the country-domain redirect: nginx sends `*.processpuzzle.hu/foo` to
 * `*.processpuzzle.com/hu/foo` (see `tools/docker/processpuzzle-testbed-frontend/nginx.conf`), and this
 * is what makes the destination mean something. Without it the prefix would simply 404 into the
 * index.html fallback and the application would come up in whatever language the configuration
 * defaults to — the redirect would move the user to a `.com` URL that says `hu` and shows English.
 *
 * **Why a serializer rather than a `:lang` route parameter.** A `{ path: ':lang', children: [...] }`
 * shell would have to wrap every route in the application, and base-app builds routes at *run time*
 * from `ModuleDefinition` metadata (`app-route-builder`'s `lazyMount`) — there is no single route array
 * to wrap. It would also change the shape every `routerLink` and every `router.navigate` call assumes.
 * The serializer sits below all of that: the router, the route configs and the links go on seeing
 * prefix-free URLs, and only the string crossing the boundary to the address bar carries the locale.
 *
 * The consequence to know is the mirror image of that strength — the locale is invisible to the route
 * tree, so a guard or resolver cannot read it as a parameter. It reads {@link TranslocoService}
 * instead, which is the source of truth here and is already what every translated component consults.
 */
@Injectable()
export class LocaleUrlSerializer extends DefaultUrlSerializer {
  private readonly translocoService = inject(TranslocoService);

  /**
   * Strips the locale prefix and, when the URL named a different language than the active one, switches
   * to it before the navigation resolves.
   *
   * Switching here rather than in a guard is deliberate: `parse` runs at the very start of a navigation,
   * so components of the incoming route render already translated. A guard would run after the route is
   * matched, which is late enough for the first render to use the outgoing language and flicker.
   *
   * The URL is therefore authoritative over the language, not the other way round — which is what makes
   * a deep link, a reload and a shared link all reproduce the sender's language.
   */
  override parse(url: string): UrlTree {
    const { locale, path } = splitLocaleFromUrl(url, this.supportedLocales());
    if (locale && locale !== this.translocoService.getActiveLang()) {
      this.translocoService.setActiveLang(locale);
    }
    return super.parse(path);
  }

  /**
   * Prefixes the active language onto the serialized tree.
   *
   * The default language is prefixed too. Leaving `en` bare would make the canonical URL of a page
   * depend on which language it is in, so a link would sometimes round-trip through
   * {@link splitLocaleFromUrl} and sometimes not, and the nginx redirect would need a special case for
   * the one country domain that maps to it. Bare URLs still *work* — `parse` leaves an unprefixed URL
   * alone — so links made before this feature keep resolving; they just gain a prefix once the router
   * next writes the address bar.
   */
  override serialize(tree: UrlTree): string {
    return withLocale(super.serialize(tree), this.translocoService.getActiveLang());
  }

  /**
   * The language codes transloco was configured with, which `provideTranslocoService` derived from
   * `LanguageConfig.AVAILABLE_LANGUAGES`. Read from transloco rather than from the runtime configuration
   * so that the set of prefixes the URL accepts cannot drift from the set of languages the application
   * can actually render.
   */
  private supportedLocales(): string[] {
    return this.translocoService.getAvailableLangs().map((language) => (typeof language === 'string' ? language : language.id));
  }
}
