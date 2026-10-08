import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MarkdownComponent } from 'ngx-markdown';
import { TranslocoDirective } from '@jsverse/transloco';

const README_ROOT = 'https://raw.githubusercontent.com/ZsZs/processpuzzle/refs/heads/develop';

/** Overview of the Event Catalog section: a short introduction, then the two libraries' READMEs. */
@Component({
  selector: 'base-event-overview',
  standalone: true,
  imports: [CommonModule, MarkdownComponent, TranslocoDirective],
  template: `
    <ng-container *transloco="let t; prefix: 'base-event'">
      <section class="intro">
        <h2>{{ t('intro_heading') }}</h2>
        <p>{{ t('intro_paragraph_1') }}</p>
        <p>{{ t('intro_paragraph_2') }}</p>
        <ul>
          <li><strong>&#64;processpuzzle/base-event</strong> — {{ t('intro_frontend_desc') }}</li>
          <li><strong>base-event-backend</strong> — {{ t('intro_backend_desc') }}</li>
        </ul>
      </section>

      <section>
        <h2>{{ t('frontend_heading') }}</h2>
        <markdown clipboard mermaid ngPreserveWhitespaces [src]="frontendReadme"></markdown>
      </section>

      <section>
        <h2>{{ t('backend_heading') }}</h2>
        <markdown clipboard mermaid ngPreserveWhitespaces [src]="backendReadme"></markdown>
      </section>
    </ng-container>
  `,
  styles: `
    section {
      padding: 16px;
      max-width: 900px;
    }
    /* The READMEs render inside MarkdownComponent's own view — see base-states' overview for why ::ng-deep. */
    :host ::ng-deep markdown h1 {
      font-size: 1.25rem;
      margin: 0 0 8px;
    }
    :host ::ng-deep markdown h2 {
      font-size: 1.1rem;
    }
  `,
})
export class OverviewComponent {
  protected readonly frontendReadme = `${README_ROOT}/libs/js-shared/base-event-frontend/README.md`;
  protected readonly backendReadme = `${README_ROOT}/libs/java-shared/base-event-backend/README.md`;
}
