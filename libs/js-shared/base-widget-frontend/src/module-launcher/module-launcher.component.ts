import { Component, computed, DestroyRef, inject } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { provideTranslocoScope, TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { DESIGN_ROUTE_PREFIX } from '@processpuzzle/util';
import { catchError, combineLatest, forkJoin, map, Observable, of, switchMap } from 'rxjs';
import { MatCardsGridComponent } from '../mat-cards-grid/mat-cards-grid.component';
import { moduleAssetPathOf } from '../module-asset/module-asset-context';
import { launcherCardsOf, ModuleLauncherEntry, MODULE_LAUNCHER_CONTEXT } from './module-launcher-context';

interface LoadedTexts {
  entries: ModuleLauncherEntry[];
  texts: Array<Record<string, string>>;
}

interface LoadedImages {
  entries: ModuleLauncherEntry[];
  urls: Array<string | undefined>;
}

/**
 * A card for every module mounted into the application that brought one — the home page of a default shell, where a
 * business starter's modules appear without the starter owning the application (business-starters design §11.4).
 *
 * The cards come from the {@link MODULE_LAUNCHER_CONTEXT} the shell provides; outside an application there is none and
 * the widget draws nothing. Inside an application with no module mounted yet it says so and points at the Business Starters,
 * so that a new organization that chose none does not land on a blank page. Each module's texts are read for the language in use and again when the user switches, a
 * picture is fetched with the user's credentials and shown from an object URL that is revoked when the page goes. A text
 * or picture that cannot be had leaves the card with the module's name and no picture, never without the card.
 *
 * The cards link relatively, as the home-page cards of a starter's application did: the page is expected to be a route of
 * the application itself, and the link climbs out of however many segments that route has.
 */
@Component({
  selector: 'pp-module-launcher',
  standalone: true,
  imports: [MatCardsGridComponent, RouterLink, TranslocoDirective],
  providers: [provideTranslocoScope({ scope: 'widgets' })],
  template: `
    @if (cards().length) {
      <pp-cards-grid [cards]="cards()" />
    } @else if (context && !entries().length) {
      <p class="module-launcher__empty" *transloco="let t; prefix: 'module_launcher'">
        {{ t('empty') }}
        <a [routerLink]="[designPrefix + '/design/starters']">{{ t('browse') }}</a>
      </p>
    }
  `,
  styles: `
    .module-launcher__empty {
      margin: 24px;
    }
  `,
})
export class ModuleLauncherComponent {
  protected readonly context = inject(MODULE_LAUNCHER_CONTEXT, { optional: true });
  protected readonly designPrefix = inject(DESIGN_ROUTE_PREFIX);
  private readonly transloco = inject(TranslocoService);
  private readonly routeDepth = inject(ActivatedRoute).snapshot.url.length;
  private readonly createdUrls = new Set<string>();

  private readonly language = toSignal(this.transloco.langChanges$, { initialValue: this.transloco.getActiveLang() });
  protected readonly entries = computed(() => this.context?.entries() ?? []);

  private readonly texts = toSignal(
    toObservable(computed(() => ({ entries: this.entries(), lang: this.language() }))).pipe(
      switchMap(({ entries, lang }) => this.loadTexts(entries, lang)),
    ),
    { initialValue: { entries: [], texts: [] } as LoadedTexts },
  );

  private readonly images = toSignal(
    toObservable(this.entries).pipe(switchMap((entries) => this.loadImages(entries))),
    { initialValue: { entries: [], urls: [] } as LoadedImages },
  );

  protected readonly cards = computed(() => {
    const entries = this.entries();
    // Texts and pictures arrive after the entries do; each is used only while it still belongs to them.
    const texts = this.texts().entries === entries ? this.texts().texts : [];
    const urls = this.images().entries === entries ? this.images().urls : [];
    return launcherCardsOf(entries, texts, urls, this.routeDepth);
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.revokeAll());
  }

  private loadTexts(entries: ModuleLauncherEntry[], lang: string): Observable<LoadedTexts> {
    const context = this.context;
    if (!context || !entries.length) return of({ entries, texts: [] });
    return forkJoin(entries.map((entry) => context.texts(entry.moduleKey, lang).pipe(catchError(() => of({} as Record<string, string>))))).pipe(
      map((texts) => ({ entries, texts })),
    );
  }

  private loadImages(entries: ModuleLauncherEntry[]): Observable<LoadedImages> {
    this.revokeAll();
    const context = this.context;
    if (!context || !entries.length) return of({ entries, urls: [] });
    return combineLatest(
      entries.map((entry) => {
        const path = moduleAssetPathOf(entry.image);
        if (path === undefined) return of(undefined);
        return context.assetBlob(entry.moduleKey, path).pipe(
          map((blob) => this.objectUrlOf(blob)),
          catchError(() => of(undefined)),
        );
      }),
    ).pipe(map((urls) => ({ entries, urls })));
  }

  private objectUrlOf(blob: Blob): string {
    const url = URL.createObjectURL(blob);
    this.createdUrls.add(url);
    return url;
  }

  private revokeAll(): void {
    this.createdUrls.forEach((url) => URL.revokeObjectURL(url));
    this.createdUrls.clear();
  }
}
