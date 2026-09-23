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
    <img
      class="thumb"
      [src]="src()"
      [alt]="alt()"
      (click)="open()"
      loading="lazy"
    />

    @if (isOpen()) {
      <div class="overlay" (click)="close()">
        <img
          #fullImg
          class="full"
          [src]="src()"
          [alt]="alt()"
          (click)="$event.stopPropagation()"
        />
        <button class="close-btn" type="button" (click)="close()" aria-label="Close">
          &times;
        </button>
      </div>
    }
  `,
  styles: `
    .thumb {
      cursor: zoom-in;
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
      cursor: zoom-out;
      animation: fade-in 0.15s ease-out;
    }

    .full {
      /* original size: no max-width/height, browser renders at natural dimensions */
      max-width: none;
      max-height: none;
      cursor: default;
      box-shadow: 0 0 40px rgba(0, 0, 0, 0.5);
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
