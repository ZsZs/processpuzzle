import { Component } from '@angular/core';

/**
 * Empty space that grows to fill the row it sits in, pushing the widgets after it to the far end.
 *
 * How a header or footer region aligns its widgets: they are laid out in one row in declaration order, so
 * `logo, title, spacer, …` puts everything after the spacer on the right, and a second spacer centres the
 * group between the two. Spacers share the free space equally, so that group is centred between its
 * neighbours rather than on the row — exact centring would need alignment zones in the region itself.
 *
 * Only meaningful in a row; in a column, such as a page's content, it grows along the other axis and does
 * nothing visible.
 */
@Component({
  selector: 'pp-spacer',
  standalone: true,
  template: '',
  host: { 'aria-hidden': 'true' },
  styles: [
    `
      :host {
        display: block;
        flex: 1 1 0;
      }
    `,
  ],
})
export class SpacerComponent {}
