import { Component } from '@angular/core';
import { ContentBookWidgetComponent } from '@processpuzzle/base-document';

/** The seeded `testbed-handbook`, read through the `content-book` widget: its contents take the sidenav over. */
@Component({
  selector: 'base-documents-handbook',
  imports: [ContentBookWidgetComponent],
  template: ` <pp-content-book bookSlug="testbed-handbook" /> `,
})
export class HandbookComponent {}
