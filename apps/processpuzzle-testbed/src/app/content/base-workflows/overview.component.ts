import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MarkdownComponent } from 'ngx-markdown';
import { TranslocoDirective } from '@jsverse/transloco';

@Component({
  selector: 'base-workflows-overview',
  standalone: true,
  imports: [CommonModule, MarkdownComponent, TranslocoDirective],
  template: `
    <ng-container *transloco="let t; prefix: 'base-workflows'">
      <section class="intro">
        <h2>{{ t('intro_heading') }}</h2>
        <p>{{ t('intro_paragraph_1') }}</p>
        <p>{{ t('intro_paragraph_2') }}</p>
        <ul>
          <li><strong>&#64;processpuzzle/base-workflow-frontend</strong> — {{ t('intro_frontend_desc') }}</li>
          <li><strong>base-workflow-backend</strong> — {{ t('intro_backend_desc') }}</li>
        </ul>
        <img src="https://raw.githubusercontent.com/ZsZs/processpuzzle/refs/heads/develop/processpuzzle-logo-small.jpg" width="240" alt="ProcessPuzzle" />
      </section>

      <section>
        <h2>{{ t('frontend_heading') }}</h2>
        <markdown clipboard mermaid ngPreserveWhitespaces [src]="'https://raw.githubusercontent.com/ZsZs/processpuzzle/refs/heads/develop/libs/js-shared/base-workflow-frontend/README.md'"></markdown>
      </section>

      <section>
        <h2>{{ t('backend_heading') }}</h2>
        <markdown clipboard mermaid ngPreserveWhitespaces [src]="'https://raw.githubusercontent.com/ZsZs/processpuzzle/refs/heads/develop/libs/java-shared/base-workflow-backend/README.md'"></markdown>
      </section>
    </ng-container>
  `,
  styles: `
    section {
      padding: 16px;
      max-width: 900px;
    }
    .intro img {
      margin-top: 16px;
    }
    /* The README renders inside MarkdownComponent's own view, so emulated encapsulation keeps a plain
       'markdown h1' selector from ever matching it — hence ::ng-deep. Without this the README's title
       (browser default 2em) outsizes the section heading above it, which is a bare h2 at 1.5em. */
    :host ::ng-deep markdown h1 {
      font-size: 1.25rem;
      margin: 0 0 8px;
    }
    :host ::ng-deep markdown h2 {
      font-size: 1.1rem;
    }
    :host ::ng-deep markdown h3 {
      font-size: 1rem;
    }
  `,
})
export class OverviewComponent {}
