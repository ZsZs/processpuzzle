import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  inject,
  input,
  OnDestroy,
  signal,
  viewChild,
} from '@angular/core';
import { LayoutService } from '@processpuzzle/util';

@Component({
  selector: 'app-image-zoom',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      class="thumb-button"
      type="button"
      (click)="open()"
      [attr.aria-label]="'Zoom image: ' + alt()"
    >
      <img class="thumb" [src]="src()" [alt]="alt()" loading="lazy" />
    </button>

    @if (isOpen()) {
      <button class="overlay" type="button" (click)="close()" aria-label="Close image preview"></button>
      <div class="dialog" role="dialog" aria-modal="true" [attr.aria-label]="alt()">
        <img
          #fullImg
          class="full"
          [src]="src()"
          [alt]="alt()"
        />
        <button class="close-btn" type="button" (click)="close()" aria-label="Close">
          &times;
        </button>
      </div>
    }
  `,
  styles: `
    .thumb-button {
      display: block;
      max-width: 100%;
      padding: 0;
      border: 0;
      background: none;
      cursor: zoom-in;
    }

    .thumb {
      max-width: 100%;
      display: block;
    }

    .overlay {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.85);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      border: 0;
      padding: 0;
      cursor: zoom-out;
      animation: fade-in 0.15s ease-out;
    }

    .dialog {
      position: fixed;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1001;
      pointer-events: none;
    }

    .full {
      /* original size: no max-width/height, browser renders at natural dimensions */
      max-width: none;
      max-height: none;
      cursor: default;
      box-shadow: 0 0 40px rgba(0, 0, 0, 0.5);
      pointer-events: auto;
    }

    .close-btn {
      position: fixed;
      top: 16px;
      right: 24px;
      font-size: 2rem;
      line-height: 1;
      background: transparent;
      border: none;
      color: #fff;
      cursor: pointer;
      pointer-events: auto;
    }

    @keyframes fade-in {
      from { opacity: 0; }
      to { opacity: 1; }
    }
  `,
})
export class ImageZoomComponent implements OnDestroy {
  // Required inputs: pass the image URL and alt text from the parent.
  readonly src = input.required<string>();
  readonly alt = input<string>('');

  readonly isOpen = signal(false);
  private readonly fullImg = viewChild<ElementRef<HTMLImageElement>>('fullImg');
  private readonly layoutService = inject(LayoutService);
  private restoreSidenav?: () => void;

  open(): void {
    this.restoreSidenav ??= this.layoutService.hideSidenav();
    this.isOpen.set(true);
  }

  close(): void {
    this.isOpen.set(false);
    this.restoreSidenav?.();
    this.restoreSidenav = undefined;
  }

  ngOnDestroy(): void {
    this.restoreSidenav?.();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.close();
  }
}
