import { afterNextRender, Component, DestroyRef, inject, Injector } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { take } from 'rxjs';
import { APP_CONCEPTS_I18N_SCOPE, BASE_APP_TRANSLOCO_SCOPE } from '../base-app.i18n';

/**
 * The concepts the guide explains, in the order an application is assembled. Each is a section with a
 * stable anchor — see {@link appConceptAnchor} — so a designer tooltip can point at exactly the paragraph
 * that explains its field.
 */
export const APP_CONCEPTS = [
  'assembling',
  'application',
  'theme',
  'layout',
  'regions',
  'header',
  'footer',
  'navigation',
  'routes',
  'widgets',
  'entities',
  'documents',
  'modules',
  'roles',
  'lifecycle',
] as const;
export type AppConcept = (typeof APP_CONCEPTS)[number];

/** Element id of a concept's section; append it as the URL fragment of a page hosting the guide. */
export function appConceptAnchor(concept: AppConcept): string {
  return `app-concept-${concept}`;
}

/**
 * The guide hosted by the application samples page: what a dynamic application is made of and how the
 * pieces relate, written for the person assembling one rather than for a developer.
 *
 * The bodies are translation values carrying a little inline markup (`<b>`, `<code>`, `<ul>`), bound
 * through `[innerHTML]` — Angular's sanitizer keeps exactly that subset, and the text is ours, not the tenant's.
 */
@Component({
  selector: 'pp-app-concepts-guide',
  standalone: true,
  imports: [TranslocoPipe],
  template: `
    <article class="guide" aria-labelledby="app-concepts-title">
      <h2 id="app-concepts-title">{{ scope + '.title' | transloco }}</h2>
      <p class="intro" [innerHTML]="scope + '.intro' | transloco"></p>
      @for (concept of concepts; track concept) {
        <section [id]="anchor(concept)">
          <h3>{{ scope + '.' + concept + '.title' | transloco }}</h3>
          <div [innerHTML]="scope + '.' + concept + '.body' | transloco"></div>
        </section>
      }
    </article>
  `,
  styles: [
    `
      .guide {
        max-width: 960px;
        padding: 16px 24px 32px;
        line-height: 1.5;
      }
      .intro {
        font-size: 1.05em;
      }
      section {
        border-top: 1px solid rgba(0, 0, 0, 0.12);
        padding-top: 8px;
        scroll-margin-top: 16px;
      }
      h3 {
        margin: 8px 0 4px;
      }
      :host ::ng-deep code {
        background: rgba(0, 0, 0, 0.06);
        border-radius: 3px;
        padding: 0 4px;
      }
    `,
  ],
})
export class AppConceptsGuideComponent {
  readonly scope = APP_CONCEPTS_I18N_SCOPE;
  readonly concepts = APP_CONCEPTS;
  readonly anchor = appConceptAnchor;
  private readonly route = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);
  private readonly injector = inject(Injector);

  constructor() {
    // A deep link from a tooltip is honoured here rather than by the router's anchor scrolling, which runs
    // before this guide exists. Not before the scope's translations arrive, either: until then every section
    // is an empty heading, and the target would slide down the page as the text above it filled in.
    const fragment = this.route.snapshot.fragment;
    if (!fragment) return;
    this.transloco
      .selectTranslation(`${BASE_APP_TRANSLOCO_SCOPE}/${this.transloco.getActiveLang()}`)
      .pipe(take(1), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => afterNextRender(() => document.getElementById(fragment)?.scrollIntoView({ block: 'start' }), { injector: this.injector }));
  }
}
