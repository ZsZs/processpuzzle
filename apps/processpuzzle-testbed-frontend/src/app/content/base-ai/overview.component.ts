import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MarkdownComponent } from 'ngx-markdown';
import { TranslocoDirective } from '@jsverse/transloco';

const README_ROOT = 'https://raw.githubusercontent.com/ZsZs/processpuzzle/refs/heads/develop';

/**
 * Overview of the Base AI section: a short introduction, then the three parts of the feature in the words
 * of their own READMEs — the Angular library, the Spring Boot library and the vision server that runs the
 * models. Same arrangement as the other base-* overviews, with a third section because this feature is the
 * first with a part that is neither a frontend nor a backend library.
 */
@Component({
  selector: 'base-ai-overview',
  standalone: true,
  imports: [CommonModule, MarkdownComponent, TranslocoDirective],
  template: `
    <ng-container *transloco="let t; prefix: 'base-ai'">
      <section class="intro">
        <h2>{{ t('intro_heading') }}</h2>
        <p>{{ t('intro_paragraph_1') }}</p>
        <p>{{ t('intro_paragraph_2') }}</p>
        <ul>
          <li><strong>&#64;processpuzzle/base-ai</strong> — {{ t('intro_frontend_desc') }}</li>
          <li><strong>base-ai-backend</strong> — {{ t('intro_backend_desc') }}</li>
          <li><strong>vision-server</strong> — {{ t('intro_vision_desc') }}</li>
        </ul>
        <img src="https://raw.githubusercontent.com/ZsZs/processpuzzle/refs/heads/develop/processpuzzle-logo-small.jpg" width="240" alt="ProcessPuzzle" />
      </section>

      <section>
        <h2>{{ t('frontend_heading') }}</h2>
        <markdown clipboard mermaid ngPreserveWhitespaces [src]="frontendReadme"></markdown>
      </section>

      <section>
        <h2>{{ t('backend_heading') }}</h2>
        <markdown clipboard mermaid ngPreserveWhitespaces [src]="backendReadme"></markdown>
      </section>

      <section>
        <h2>{{ t('vision_heading') }}</h2>
        <markdown clipboard mermaid ngPreserveWhitespaces [src]="visionReadme"></markdown>
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
    /* The READMEs render inside MarkdownComponent's own view — see base-states' overview for why ::ng-deep. */
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
export class OverviewComponent {
  protected readonly frontendReadme = `${README_ROOT}/libs/js-shared/base-ai-frontend/README.md`;
  protected readonly backendReadme = `${README_ROOT}/libs/java-shared/base-ai-backend/README.md`;
  protected readonly visionReadme = `${README_ROOT}/apps/vision-server/README.md`;
}
