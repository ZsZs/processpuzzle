import { afterNextRender, DestroyRef, Directive, ElementRef, inject } from '@angular/core';
import { MatSidenavContainer } from '@angular/material/sidenav';

/**
 * Keeps the sidenav content margin in step with a content-sized (`width: auto`) `mat-sidenav`.
 *
 * `mat-sidenav-container`'s own `autosize` re-measures the drawer in `ngDoCheck`, so it only notices a
 * width change when change detection happens to run. In a zoneless application nothing runs it when the
 * drawer resizes on its own — typically when the icon font arrives and ligature names like `settings`
 * collapse into glyphs, or when async translations replace the labels — and the content keeps the stale,
 * wider margin until the next window resize. A `ResizeObserver` reports exactly those changes, and
 * `updateContentMargins()` marks the content for check, which schedules change detection zoneless too.
 */
@Directive({ selector: 'mat-sidenav[ppSidenavAutosize]', standalone: true })
export class SidenavAutosizeDirective {
  private readonly container = inject(MatSidenavContainer);
  private readonly drawer = inject<ElementRef<HTMLElement>>(ElementRef);

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      if (typeof ResizeObserver === 'undefined') return;
      const observer = new ResizeObserver(() => this.container.updateContentMargins());
      observer.observe(this.drawer.nativeElement);
      destroyRef.onDestroy(() => observer.disconnect());
    });
  }
}
