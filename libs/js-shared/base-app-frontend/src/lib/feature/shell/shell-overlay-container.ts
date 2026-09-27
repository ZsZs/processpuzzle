import { OverlayContainer } from '@angular/cdk/overlay';
import { ElementRef, inject, Injectable } from '@angular/core';

/**
 * An `OverlayContainer` that lives inside the shell's host element instead of under `<body>`.
 *
 * The shell's theme is a set of custom properties and a `color-scheme` on its host, and they only cascade to
 * descendants. A menu, tooltip or dialog opened from inside the shell renders into the overlay container, so
 * with CDK's default one it would wear the surrounding application's theme — a light menu over a dark
 * previewed app. Moving the container into the host is all it takes; the container is `position: fixed`, and
 * native popovers render in the top layer regardless of where they sit in the DOM.
 *
 * Provided in `AppShellComponent`'s `providers`, which is also where `ElementRef` comes from. Menus and
 * tooltips resolve it through their own element injector; dialogs need `Dialog` and `MatDialog` provided
 * there as well, since the root ones would resolve the root container.
 */
@Injectable()
export class ShellOverlayContainer extends OverlayContainer {
  private readonly host: HTMLElement = inject(ElementRef).nativeElement;

  protected override _createContainer(): void {
    super._createContainer();
    if (this._containerElement) this.host.appendChild(this._containerElement);
  }
}
