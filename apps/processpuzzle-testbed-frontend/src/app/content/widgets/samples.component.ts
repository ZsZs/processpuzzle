import { Component } from '@angular/core';
import { ImageZoomComponent } from '@processpuzzle/widgets';

@Component({
  selector: 'app-widgets-samples',
  standalone: true,
  imports: [ImageZoomComponent],
  template: `
    <section>
      <h1>Image zoom</h1>
      <p>Click the image to view it at its original size.</p>
      <app-image-zoom class="image-zoom-sample" src="assets/Conduct_Race.png" alt="Conduct Race diagram" />
    </section>
  `,
  styles: `
    .image-zoom-sample {
      display: block;
      width: 40%;
    }
  `,
})
export class WidgetsSamplesComponent {}
