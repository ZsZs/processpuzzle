import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MarkdownComponent } from 'ngx-markdown';
import { TranslocoDirective } from '@jsverse/transloco';

@Component({
  selector: 'base-rules-overview',
  standalone: true,
  imports: [CommonModule, MarkdownComponent, TranslocoDirective],
  template: `
    <ng-container *transloco="let t; prefix: 'base-rules'">
      <section class="intro">
        <h2>{{ t('intro_heading') }}</h2>
        <p>{{ t('intro_paragraph_1') }}</p>
        <p>{{ t('intro_paragraph_2') }}</p>
        <ul>
          <li><strong>&#64;processpuzzle/base-rule-frontend</strong> — {{ t('intro_frontend_desc') }}</li>
          <li><strong>base-rule-backend</strong> — {{ t('intro_backend_desc') }}</li>
        </ul>
        <img src="https://raw.githubusercontent.com/ZsZs/processpuzzle/refs/heads/develop/processpuzzle-logo-small.jpg" width="240" alt="ProcessPuzzle" />
      </section>

      <section>
        <h2>{{ t('frontend_heading') }}</h2>
        <markdown clipboard mermaid ngPreserveWhitespaces [src]="'https://raw.githubusercontent.com/ZsZs/processpuzzle/refs/heads/develop/libs/js-shared/base-rule-frontend/README.md'"></markdown>
      </section>

      <section>
        <h2>{{ t('backend_heading') }}</h2>
        <markdown clipboard mermaid ngPreserveWhitespaces [src]="'https://raw.githubusercontent.com/ZsZs/processpuzzle/refs/heads/develop/libs/java-shared/base-rule-backend/README.md'"></markdown>
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
  `,
})
export class OverviewComponent {}
