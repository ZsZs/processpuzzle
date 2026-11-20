import { InjectionToken, Signal } from '@angular/core';
import { Observable } from 'rxjs';
import { CardsGridSpec } from '../mat-cards-grid/cards-spec';

/**
 * The card an application's launcher draws for one mounted module — what a business starter's mount hint described.
 * Texts are keys of the module's own translations, relative to its scope; `image` is an asset of the module
 * (`module:<path>`); `link` is the module route the card opens, relative to the mount.
 */
export interface ModuleLauncherEntry {
  moduleKey: string;
  /** The module's name: the card's title until a translation says otherwise. */
  name: string;
  basePath: string;
  image?: string;
  titleKey: string;
  subtitleKey?: string;
  contentKeys: string[];
  link?: string;
}

/**
 * What the `module-launcher` widget may know about the application it is placed in: which mounted modules brought a
 * launcher card, and how to read their texts and pictures. Provided by base-app's shell from the `AppDefinition` it
 * renders — declared here because this library sits below base-app, as {@link APPLICATION_CONTEXT} is.
 *
 * Texts and pictures are asked for the module by key rather than resolved through transloco and the module asset
 * context, because a module's scope loads and its assets are served only once the user is *inside* the module, while
 * the launcher is a page of the application that has not entered any.
 */
export interface ModuleLauncherContext {
  readonly entries: Signal<ModuleLauncherEntry[]>;
  /** The module's stored translations for one language, keys relative to its scope; empty when there are none. */
  texts(moduleKey: string, lang: string): Observable<Record<string, string>>;
  /** An asset of the module, as the module asset context hands it to a widget inside the module. */
  assetBlob(moduleKey: string, path: string): Observable<Blob>;
}

export const MODULE_LAUNCHER_CONTEXT = new InjectionToken<ModuleLauncherContext>('MODULE_LAUNCHER_CONTEXT');

/**
 * The cards of a launcher, one per entry, in order. A card with no translation of its title is titled with the module's
 * name; a subtitle or a text line with none is left out, because a key shown as itself would be noise on a home page.
 *
 * Every text is handed to the cards grid already resolved: it resolves its texts as transloco keys under a prefix, and a
 * text that is no key renders as itself, so no prefix is needed. `routeDepth` is how many URL segments the page the
 * launcher sits on has, which the relative link climbs back out of to reach the mount. Each card also has a button
 * leading to the same place, captioned `goCaption`.
 */
export function launcherCardsOf(
  entries: readonly ModuleLauncherEntry[],
  texts: ReadonlyArray<Record<string, string>>,
  imageUrls: ReadonlyArray<string | undefined>,
  routeDepth: number,
  goCaption = 'Go...',
): CardsGridSpec[] {
  const up = '../'.repeat(routeDepth);
  return entries.map((entry, index) => {
    const own = texts[index] ?? {};
    const landing = entry.link ? `/${entry.link.replace(/^\/+/, '')}` : '';
    const link = `${up}${entry.basePath}${landing}`;
    return {
      title: own[entry.titleKey] || entry.name,
      subtitle: (entry.subtitleKey && own[entry.subtitleKey]) || '',
      content: entry.contentKeys.map((key) => own[key]).filter((text): text is string => !!text),
      actions: [{ caption: goCaption, link }],
      translocoPrefix: '',
      link,
      ...(imageUrls[index] ? { backgroundImage: imageUrls[index] } : {}),
    };
  });
}
