import { ChangeDetectionStrategy, Component } from '@angular/core';
import { provideTranslocoScope, TranslocoPipe } from '@jsverse/transloco';
import { AssistantPanelComponent } from '@processpuzzle/base-ai';

/**
 * The AI Designer section: the design assistant on a page of its own, reached from the Designer Home card. The
 * panel keeps the conversation; what the assistant drafts is reviewed in the other designers, and nothing it writes
 * goes live before a release. The notice says what the assistant needs before anyone types, since a refused message
 * is the only other place the requirement would show.
 */
@Component({
  selector: 'pp-ai-designer',
  imports: [AssistantPanelComponent, TranslocoPipe],
  providers: [provideTranslocoScope({ scope: 'design' })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="pp-ai-designer__requirement">{{ 'assistant_requirement' | transloco }}</p>
    <pp-assistant-panel class="pp-ai-designer" />
  `,
  styles: [
    `
      .pp-ai-designer__requirement {
        max-width: 56rem;
        margin: 1rem auto 0;
        padding: 0 1rem;
        font-style: italic;
      }

      .pp-ai-designer {
        display: block;
        max-width: 56rem;
        margin: 0 auto;
        padding: 1rem;
      }
    `,
  ],
})
export class AiDesignerComponent {}
