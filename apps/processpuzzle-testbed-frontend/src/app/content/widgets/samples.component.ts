import { Component, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatChip, MatChipSet } from '@angular/material/chips';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { CopyrightComponent, ImageZoomComponent, PhotoAlbumComponent, PhotoAlbumImage, ThemeSelection, ThemeService, ThemesButtonComponent } from '@processpuzzle/widgets';

/**
 * A themed box around the ThemesButton sample. It provides its own ThemeService, so a choice made here themes
 * this box only — with the root one, the sample would retheme the whole testbed and keep it that way. Its
 * choices are kept in memory only, for the same reason.
 */
@Component({
  selector: 'app-themes-button-sample',
  standalone: true,
  imports: [ThemesButtonComponent, MatButton, MatChipSet, MatChip, MatSlideToggle],
  providers: [ThemeService],
  host: { '[class]': 'theme.themeClass()' },
  template: `
    <div class="themes-sample__toolbar">
      <span>Pick a theme:</span>
      <pp-themes-button (themeChange)="lastChange = $event" />
    </div>
    <div class="themes-sample__controls">
      <button mat-flat-button>Flat</button>
      <button mat-stroked-button>Stroked</button>
      <mat-chip-set><mat-chip>Chip</mat-chip></mat-chip-set>
      <mat-slide-toggle checked>Toggle</mat-slide-toggle>
    </div>
    <p class="themes-sample__status">{{ lastChange ? 'themeChange: ' + lastChange.preset + ' / ' + lastChange.scheme : 'No change yet.' }}</p>
  `,
  styles: `
    :host {
      background: var(--mat-sys-surface);
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: 8px;
      color: var(--mat-sys-on-surface);
      display: block;
      padding: 12px 16px;
      width: fit-content;
    }
    .themes-sample__toolbar {
      align-items: center;
      background: var(--pp-surface-header);
      color: var(--pp-on-header);
      display: flex;
      gap: 8px;
      padding: 0 8px;
    }
    .themes-sample__controls {
      align-items: center;
      display: flex;
      gap: 12px;
      padding: 12px 0;
    }
  `,
})
export class ThemesButtonSampleComponent {
  protected readonly theme = inject(ThemeService);
  protected lastChange: ThemeSelection | undefined;
}

@Component({
  selector: 'app-widgets-samples',
  standalone: true,
  imports: [CopyrightComponent, ImageZoomComponent, PhotoAlbumComponent, ThemesButtonSampleComponent],
  template: `
    <section>
      <h1>Copyright</h1>
      <p>Displays a configurable copyright notice.</p>
      <pp-copyright [text]="copyrightText" />

      <table>
        <caption>
          Inputs
        </caption>
        <thead>
          <tr>
            <th scope="col">Name</th>
            <th scope="col">Type</th>
            <th scope="col">Required</th>
            <th scope="col">Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>text</td>
            <td>string</td>
            <td>Yes</td>
            <td>The copyright notice to display.</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section>
      <h1>Themes button</h1>
      <p>
        Picks one of the theme presets and a light, dark or automatic colour scheme for the nearest ThemeService: the whole document in a host application's
        toolbar, one application inside a base-app shell. Here it themes only the box below.
      </p>
      <app-themes-button-sample />

      <table>
        <caption>
          Outputs
        </caption>
        <thead>
          <tr>
            <th scope="col">Name</th>
            <th scope="col">Type</th>
            <th scope="col">Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>themeChange</td>
            <td>ThemeSelection</td>
            <td>Emitted on every pick or reset, with the resulting preset and scheme.</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section>
      <h1>Image zoom</h1>
      <p>Click the image to view it at its original size.</p>
      <app-image-zoom class="image-zoom-sample" src="assets/Conduct_Race.png" alt="Conduct Race diagram" />
    </section>

    <section>
      <h1>Photo album</h1>
      <p>Use the arrows to browse the images, then select one to view it at its original size.</p>
      <app-photo-album [images]="images" [maxHeight]="240" />
    </section>
  `,
  styles: `
    .image-zoom-sample {
      display: block;
      width: 40%;
    }

    table {
      border-collapse: collapse;
    }

    th,
    td {
      border: 1px solid currentColor;
      padding: 4px 8px;
    }
  `,
})
export class WidgetsSamplesComponent {
  readonly copyrightText = 'Zsolt Zsuffa 2026';
  readonly images: readonly PhotoAlbumImage[] = [
    { src: 'assets/Analyse_Race.png', alt: 'Analyse Race diagram' },
    { src: 'assets/Conduct_Race.png', alt: 'Conduct Race diagram' },
    { src: 'assets/Plan_Race.png', alt: 'Plan Race diagram' },
  ];
}
