import { inject, Injectable } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { firstValueFrom, Subscription } from 'rxjs';
import { AppTranslationService } from '../../domain/app-translation.service';

/**
 * Brings the texts of an application's own pages — a home page's cards, say — to the widgets that name them. They
 * are merged into the translations of every language rather than into a scope of their own: the application's
 * widgets resolve their keys under whatever `translocoPrefix` the designer typed, in the root scope, so a key the
 * author uploaded is found wherever it is looked for.
 *
 * <p>What is stored is read in the channel the routes were built from — the draft in the designer's Preview, the
 * released one in the running application. The shipped translations of a language are loaded first and the uploaded
 * texts merged over them, never the other way round, so an uploaded text wins and nothing shipped is lost. A language
 * with nothing stored, or whose read fails, simply adds nothing: the widgets then show what they showed before, the
 * keys themselves.
 */
@Injectable({ providedIn: 'root' })
export class AppTranslationsLoader {
  private readonly transloco = inject(TranslocoService);
  private readonly service = inject(AppTranslationService);
  private watching?: Subscription;

  /**
   * Merges the application's texts, for the language in use at once and for the others in the background, and again
   * for each language the user switches to later. Replaces what an earlier call arranged: one application is live at
   * a time.
   */
  async activate(appId: string, revision: 'draft' | 'released'): Promise<void> {
    this.watching?.unsubscribe();
    const merged = new Set<string>();
    const mergeOnce = (lang: string): Promise<void> => {
      if (merged.has(lang)) return Promise.resolve();
      merged.add(lang);
      return this.merge(appId, lang, revision);
    };
    const active = this.transloco.getActiveLang();
    await mergeOnce(active);
    for (const lang of this.languages()) void mergeOnce(lang);
    this.watching = this.transloco.langChanges$.subscribe((lang) => void mergeOnce(lang));
  }

  private async merge(appId: string, lang: string, revision: 'draft' | 'released'): Promise<void> {
    let texts: Record<string, string>;
    try {
      texts = await firstValueFrom(this.service.bundle(appId, lang, revision));
    } catch {
      return;
    }
    if (!Object.keys(texts ?? {}).length) return;
    try {
      // The shipped file first: it merges *into* what is there, so loading it afterwards would overwrite the uploads.
      await firstValueFrom(this.transloco.load(lang));
    } catch {
      // A language with no shipped file still takes the uploaded texts.
    }
    this.transloco.setTranslation(texts, lang, { merge: true });
  }

  /** The languages transloco is configured for, and the fallback one. */
  private languages(): string[] {
    const available = this.transloco.getAvailableLangs().map((lang) => (typeof lang === 'string' ? lang : lang.id));
    return [...new Set([...available, 'en'])];
  }
}
