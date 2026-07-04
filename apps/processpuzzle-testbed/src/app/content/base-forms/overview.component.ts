import { Component } from '@angular/core';
import { MarkdownComponent } from 'ngx-markdown';

@Component({
  selector: 'base-entity-overview',
  standalone: true,
  imports: [MarkdownComponent],
  template: ` <markdown clipboard mermaid [src]="'https://raw.githubusercontent.com/ZsZs/processpuzzle/refs/heads/develop/libs/js-shared/base-entity-frontend/README.md'" ngPreserveWhitespaces></markdown> `,
})
export class OverviewComponent {}
