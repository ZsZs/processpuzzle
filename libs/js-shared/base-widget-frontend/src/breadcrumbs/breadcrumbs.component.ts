import { Component, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIcon } from '@angular/material/icon';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { filter } from 'rxjs';
import { BreadcrumbItem, readBreadcrumbTrail } from './breadcrumb-trail';

const HOME_KEY = 'base_widget.breadcrumbs.home';

/** A crumb as rendered; `url` is absent for the current page and for the collapsed "…". */
interface RenderedCrumb {
  text: string;
  url?: string;
}

/**
 * The path to the current page, derived from the router so that nothing has to be configured per application.
 * See {@link readBreadcrumbTrail} for how a route earns a crumb.
 */
@Component({
  selector: 'pp-breadcrumbs',
  imports: [MatIcon, RouterLink],
  template: `
    @if (crumbs().length > 0) {
      <nav class="pp-breadcrumbs" aria-label="Breadcrumb" test-id="breadcrumbs">
        <ol>
          @for (crumb of crumbs(); track $index; let isLast = $last) {
            <li>
              @if (crumb.url) {
                <a [routerLink]="crumb.url">{{ crumb.text }}</a>
              } @else if (isLast) {
                <span aria-current="page">{{ crumb.text }}</span>
              } @else {
                <span aria-hidden="true">{{ crumb.text }}</span>
              }
              @if (!isLast) {
                <mat-icon class="pp-breadcrumbs__separator" aria-hidden="true">chevron_right</mat-icon>
              }
            </li>
          }
        </ol>
      </nav>
    }
  `,
  styles: [
    `
      :host {
        display: inline-block;
        min-width: 0;
      }
      ol {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        margin: 0;
        padding: 0;
        list-style: none;
      }
      li {
        display: inline-flex;
        align-items: center;
      }
      a {
        color: inherit;
        text-decoration: none;
      }
      a:hover {
        text-decoration: underline;
      }
      [aria-current='page'] {
        font-weight: 500;
      }
      .pp-breadcrumbs__separator {
        width: 18px;
        height: 18px;
        font-size: 18px;
        opacity: 0.6;
      }
    `,
  ],
})
export class BreadcrumbsComponent {
  /** Puts the application's root first. */
  readonly showHome = input<boolean>(true);
  /** The most crumbs to show; a longer trail keeps the first and the last ones and collapses the middle. 0 is no limit. */
  readonly maxItems = input<number>(0);

  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly trail = signal<BreadcrumbItem[]>(readBreadcrumbTrail(this.router.routerState.snapshot.root));
  // Reading it makes the labels follow a language switch.
  private readonly language = toSignal(this.transloco.langChanges$);

  protected readonly crumbs = computed<RenderedCrumb[]>(() => {
    this.language();
    const items = [...this.trail()];
    if (this.showHome() && items[0]?.url !== '/') items.unshift({ label: HOME_KEY, url: '/' });
    const crumbs: RenderedCrumb[] = items.map((item, index) => ({ text: this.transloco.translate(item.label), url: index === items.length - 1 ? undefined : item.url }));
    return this.collapse(crumbs);
  });

  constructor() {
    this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => this.trail.set(readBreadcrumbTrail(this.router.routerState.snapshot.root)));
  }

  private collapse(crumbs: RenderedCrumb[]): RenderedCrumb[] {
    const max = this.maxItems();
    if (max < 3 || crumbs.length <= max) return crumbs;
    return [crumbs[0], { text: '…' }, ...crumbs.slice(crumbs.length - (max - 2))];
  }
}
