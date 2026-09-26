import { Component, input } from '@angular/core';

/**
 * Displays a copyright notice whose text is supplied by the embedding application.
 */
@Component({
  selector: 'pp-copyright',
  template: `<span class="copyright">&copy; {{ text() }}</span>`,
})
export class CopyrightComponent {
  readonly text = input.required<string>();
}
