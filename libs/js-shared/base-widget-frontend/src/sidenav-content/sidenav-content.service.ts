import { computed, Injectable, signal } from '@angular/core';
import { Portal } from '@angular/cdk/portal';

export type SidenavContentMode = 'content' | 'menu';

/**
 * Lets a page put its own navigation into the application's sidenav — a book's table of contents, say —
 * in place of the menu, while leaving the reader one click away from the menu.
 *
 * The page owns the content and attaches it as a CDK {@link Portal}; the shell owns the sidenav and renders
 * whatever is attached through a `pp-sidenav-content-outlet`. Neither knows the other, which is what lets
 * the same page work in the testbed shell and in a base-app `AppShellComponent`.
 *
 * Attaching returns its own detach, as `LayoutService.hideSidenav()` does: a page detaches in its own
 * `DestroyRef`, and a stale detach — one whose portal has since been replaced by another page — is a no-op
 * rather than taking the newer page's content down with it.
 *
 * A page should check {@link hasOutlet} first: an application whose layout has no sidenav renders no outlet,
 * and content attached there would simply never be seen.
 */
@Injectable({ providedIn: 'root' })
export class SidenavContentService {
  private readonly attached = signal<{ portal: Portal<unknown>; title: string } | undefined>(undefined);
  private readonly outlets = signal(0);

  readonly portal = computed(() => this.attached()?.portal);
  readonly title = computed(() => this.attached()?.title ?? '');

  /** Which of the two the sidenav shows while content is attached. Content first: that is why it was attached. */
  readonly mode = signal<SidenavContentMode>('content');

  /** Whether the sidenav shows the attached content rather than the menu. */
  readonly active = computed(() => this.portal() !== undefined && this.mode() === 'content');

  /** Whether a sidenav is there to show attached content at all. */
  readonly hasOutlet = computed(() => this.outlets() > 0);

  /** Called by a `pp-sidenav-content-outlet`; returns its own unregister. */
  registerOutlet(): () => void {
    this.outlets.update((count) => count + 1);
    let unregistered = false;

    return () => {
      if (unregistered) return;

      unregistered = true;
      this.outlets.update((count) => Math.max(0, count - 1));
    };
  }

  attach(portal: Portal<unknown>, title: string): () => void {
    const entry = { portal, title };
    this.attached.set(entry);
    this.mode.set('content');

    return () => {
      if (this.attached() !== entry) return;

      this.attached.set(undefined);
      this.mode.set('content');
    };
  }

  /** Switches between the attached content and the menu. */
  toggle(): void {
    this.mode.update((mode) => (mode === 'content' ? 'menu' : 'content'));
  }
}
