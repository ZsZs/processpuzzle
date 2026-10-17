import { computed, Directive, HostListener, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { BaseFormNavigatorSingletonStore } from '@processpuzzle/base-entity';

/**
 * A link out of the dashboard that the screen it opens can come back from.
 *
 * Not a `routerLink`: that navigates past `BaseFormNavigatorSingletonStore`, which then has no `returnTo`,
 * so the opened record's Cancel — `navigateBack()` — falls back to the entity's own list instead of the
 * dashboard. A plain click goes through `navigateToUrl` instead, which records the current URL first.
 *
 * The `href` is still the real URL, so a modified or middle click opens a new tab as any link does; that
 * tab has nowhere to return to, and lands on the list as before.
 */
@Directive({
  selector: 'a[ppReturningLink]',
  standalone: true,
  host: { '[attr.href]': 'href()' },
})
export class ReturningLinkDirective {
  readonly ppReturningLink = input.required<string>();
  readonly queryParams = input<Record<string, string> | undefined>(undefined);

  private readonly router = inject(Router);
  private readonly navigator = inject(BaseFormNavigatorSingletonStore);

  protected readonly href = computed(() => this.router.serializeUrl(this.router.createUrlTree([this.ppReturningLink()], { queryParams: this.queryParams() })));

  @HostListener('click', ['$event'])
  onClick(event: MouseEvent): boolean {
    if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return true;
    void this.navigator.navigateToUrl(this.href());
    return false;
  }
}
