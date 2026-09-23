import { Component } from '@angular/core';
import { ImageZoomComponent, PhotoAlbumComponent, PhotoAlbumImage } from '@processpuzzle/widgets';

@Component({
  selector: 'app-widgets-samples',
  standalone: true,
  imports: [ImageZoomComponent, PhotoAlbumComponent],
  template: `
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
  `,
})
export class WidgetsSamplesComponent {
  readonly images: readonly PhotoAlbumImage[] = [
    { src: 'assets/Analyse_Race.png', alt: 'Analyse Race diagram' },
    { src: 'assets/Conduct_Race.png', alt: 'Conduct Race diagram' },
    { src: 'assets/Plan_Race.png', alt: 'Plan Race diagram' },
  ];
}
