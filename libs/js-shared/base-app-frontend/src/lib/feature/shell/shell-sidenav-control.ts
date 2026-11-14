import { Injectable, signal } from '@angular/core';
import { SidenavControl } from '@processpuzzle/widgets';

/**
 * The shell's answer to base-widget's `SIDENAV_CONTROL`, for the `sidenav-toggle` widget. The shell owns the
 * drawer and writes {@link available}; the widget only flips {@link opened}, which the shell binds to it.
 */
@Injectable()
export class ShellSidenavControl implements SidenavControl {
  readonly available = signal(false);
  readonly opened = signal(true);

  toggle(): void {
    this.opened.update((opened) => !opened);
  }
}
