import { Component } from '@angular/core';
import { CopyrightComponent, ImageZoomComponent, PhotoAlbumComponent, PhotoAlbumImage } from '@processpuzzle/widgets';

@Component({
  selector: 'app-widgets-samples',
  standalone: true,
  imports: [CopyrightComponent, ImageZoomComponent, PhotoAlbumComponent],
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
