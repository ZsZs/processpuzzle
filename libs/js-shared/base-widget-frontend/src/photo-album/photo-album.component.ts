import { ChangeDetectionStrategy, Component, computed, HostListener, inject, input, OnDestroy, signal } from '@angular/core';
import { LayoutService } from '@processpuzzle/util';

export interface PhotoAlbumImage {
  readonly src: string;
  readonly alt: string;
}

@Component({
  selector: 'app-photo-album',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (currentImage(); as image) {
      <div class="embedded" [style.--photo-album-max-height]="maxHeightCss()">
        <button class="image-button" type="button" (click)="open()">
          <img class="embedded-image" [src]="image.src" [alt]="image.alt" loading="lazy" />
        </button>

        @if (images().length > 1) {
          <button class="embedded-control previous" type="button" aria-label="Previous image" [disabled]="!hasPrevious()" (click)="previous()">&lt;</button>
          <button class="embedded-control next" type="button" aria-label="Next image" [disabled]="!hasNext()" (click)="next()">&gt;</button>
        }
      </div>
    }

    @if (isOpen()) {
      @if (currentImage(); as image) {
        <div class="overlay" role="dialog" aria-modal="true" [attr.aria-label]="image.alt">
          <button class="backdrop" type="button" aria-label="Close image" (click)="close()"></button>
          <img class="full" [src]="image.src" [alt]="image.alt" />

          @if (images().length > 1) {
            <button class="viewer-control previous" type="button" aria-label="Previous image in viewer" [disabled]="!hasPrevious()" (click)="previous()">&lt;</button>
            <button class="viewer-control next" type="button" aria-label="Next image in viewer" [disabled]="!hasNext()" (click)="next()">&gt;</button>
          }

          <button class="close-button" type="button" aria-label="Close" (click)="close()">&times;</button>
        </div>
      }
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .embedded {
      position: relative;
      display: inline-block;
    }

    .image-button {
      display: block;
      padding: 0;
      border: 0;
      background: transparent;
      cursor: zoom-in;
    }

    .embedded-image {
      display: block;
      max-width: 100%;
      max-height: var(--photo-album-max-height);
      object-fit: contain;
    }

    .embedded-control,
    .viewer-control {
      position: absolute;
      z-index: 2;
      top: 50%;
      transform: translateY(-50%);
      border: 0;
      background: rgba(0, 0, 0, 0.55);
      color: #fff;
      cursor: pointer;
      font-size: 2rem;
      line-height: 1;
    }

    .embedded-control {
      padding: 0.25rem 0.5rem;
    }

    .previous {
      left: 0.5rem;
    }

    .next {
      right: 0.5rem;
    }

    .embedded-control:disabled,
    .viewer-control:disabled {
      cursor: default;
      opacity: 0.4;
    }

    .overlay {
      position: fixed;
      inset: 0;
      z-index: 1000;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(0, 0, 0, 0.85);
      cursor: zoom-out;
      animation: fade-in 0.15s ease-out;
    }

    .backdrop {
      position: absolute;
      inset: 0;
      border: 0;
      background: transparent;
      cursor: zoom-out;
    }

    .full {
      position: relative;
      z-index: 1;
      max-width: none;
      max-height: none;
      cursor: default;
      box-shadow: 0 0 40px rgba(0, 0, 0, 0.5);
    }

    .viewer-control {
      position: fixed;
      padding: 0.5rem 0.75rem;
    }

    .close-button {
      position: fixed;
      top: 16px;
      right: 24px;
      z-index: 1;
      border: 0;
      background: transparent;
      color: #fff;
      cursor: pointer;
      font-size: 2rem;
      line-height: 1;
    }

    @keyframes fade-in {
      from {
        opacity: 0;
      }
      to {
        opacity: 1;
      }
    }
  `,
})
export class PhotoAlbumComponent implements OnDestroy {
  readonly images = input.required<readonly PhotoAlbumImage[]>();
  readonly maxHeight = input.required<string | number>();

  readonly selectedIndex = signal(0);
  readonly currentImage = computed(() => this.images()[this.selectedIndex()]);
  readonly hasPrevious = computed(() => this.selectedIndex() > 0);
  readonly hasNext = computed(() => this.selectedIndex() < this.images().length - 1);
  readonly isOpen = signal(false);
  private readonly layoutService = inject(LayoutService);
  private restoreSidenav?: () => void;

  maxHeightCss(): string {
    const maxHeight = this.maxHeight();
    return typeof maxHeight === 'number' ? `${maxHeight}px` : maxHeight;
  }

  previous(): void {
    if (this.hasPrevious()) {
      this.selectedIndex.update((index) => index - 1);
    }
  }

  next(): void {
    if (this.hasNext()) {
      this.selectedIndex.update((index) => index + 1);
    }
  }

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
