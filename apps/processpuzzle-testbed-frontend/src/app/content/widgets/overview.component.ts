import { Component } from '@angular/core';
import { MarkdownComponent } from 'ngx-markdown';

@Component({
  selector: 'app-widgets-overview',
  standalone: true,
  imports: [MarkdownComponent],
  template: `
    <markdown
      [src]="'https://raw.githubusercontent.com/ZsZs/processpuzzle/refs/heads/develop/libs/js-shared/base-widget-frontend/README.md'">
    </markdown>
  `,
})
export class WidgetsOverviewComponent {}
