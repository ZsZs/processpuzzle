import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, ElementRef, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MarkdownComponent } from 'ngx-markdown';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MODULE_ASSET_CONTEXT, moduleAssetPathOf, resolveAssetLink } from '../module-asset/module-asset-context';

@Component({
  selector: 'pp-markdown-page',
  imports: [CommonModule, MarkdownComponent],
  template: `
    @if (assetPath() === undefined) {
      <markdown [src]="src()" (load)="onLoad()" (error)="onError($event)"></markdown>
    } @else if (text() !== undefined) {
      <markdown [data]="text()" (ready)="onAssetRendered()" (click)="onAssetClick($event)"></markdown>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarkdownPageComponent {
  private readonly snackBar = inject(MatSnackBar);
  private readonly assets = inject(MODULE_ASSET_CONTEXT, { optional: true });
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly objectUrls: string[] = [];

  /**
   * Bound by a widget placement's props, or from route data by `withComponentInputBinding()`. An ordinary URL,
   * or `module:<path>` for a markdown file the module itself brought along.
   */
  readonly markdownSrc = input<string>('');
  readonly src = computed(() => this.markdownSrc() ?? '');
  /** The module path of the file, when it is one of the module's. */
  protected readonly assetPath = computed(() => moduleAssetPathOf(this.src()));
  /** The text of a module file, fetched through the asset context because the request needs the bearer token. */
  protected readonly text = signal<string | undefined>(undefined);

  constructor() {
    effect((onCleanup) => {
      const path = this.assetPath();
      this.text.set(undefined);
      if (path === undefined) return;
      if (!this.assets) {
        this.onError(`'${path}' is a module asset, but this page is not part of a module.`);
        return;
      }
      const subscription = this.assets.blob(path).subscribe({
        next: (blob) => void blob.text().then((content) => this.text.set(content)),
        error: (failure: unknown) => this.onError(failure instanceof Error ? failure : String(failure)),
      });
      onCleanup(() => subscription.unsubscribe());
    });
    inject(DestroyRef).onDestroy(() => this.revokeAll());
  }

  // region event handling methods
  onLoad() {
    // no-op: template binding requires a handler
  }

  onError($event: string | Error) {
    const message = typeof $event === 'string' ? $event : $event.message;
    this.snackBar.open(`Error loading markdown: ${message}`, 'Close', {
      duration: 5000,
      horizontalPosition: 'center',
      verticalPosition: 'bottom',
    });
  }

  /**
   * The rendered page of a module file may point at other files of the module — `![flow](img/flow.png)` — which a
   * browser would look up beside the *application*, and which would need the bearer token anyway. Each such image is
   * fetched through the asset context and shown from an object URL instead.
   */
  protected onAssetRendered(): void {
    const from = this.assetPath();
    if (from === undefined || !this.assets) return;
    this.revokeAll();
    for (const image of Array.from(this.host.nativeElement.querySelectorAll<HTMLImageElement>('img[src]'))) {
      const path = resolveAssetLink(from, image.getAttribute('src'));
      if (path === undefined) continue;
      this.assets.blob(path).subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          this.objectUrls.push(url);
          image.src = url;
        },
        error: () => image.removeAttribute('src'),
      });
    }
  }

  /** A link to another file of the module opens it from an object URL; any other link is left to the browser. */
  protected onAssetClick(event: Event): void {
    const from = this.assetPath();
    const anchor = (event.target as HTMLElement | null)?.closest('a');
    const path = from === undefined || !anchor ? undefined : resolveAssetLink(from, anchor.getAttribute('href'));
    if (path === undefined || !this.assets) return;
    event.preventDefault();
    this.assets.blob(path).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        this.objectUrls.push(url);
        window.open(url, '_blank', 'noopener');
      },
      error: (failure: unknown) => this.onError(failure instanceof Error ? failure : String(failure)),
    });
  }
  // endregion

  private revokeAll(): void {
    this.objectUrls.splice(0).forEach((url) => URL.revokeObjectURL(url));
  }
}
