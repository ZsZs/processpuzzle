import { ChangeDetectionStrategy, Component } from '@angular/core';
import { AssistantPanelComponent } from '@processpuzzle/base-ai';

/**
 * The AI Designer section: the design assistant on a page of its own, reached from the Designer Home card. The
 * panel keeps the conversation; what the assistant drafts is reviewed in the other designers, and nothing it writes
 * goes live before a release.
 */
@Component({
  selector: 'pp-ai-designer',
  imports: [AssistantPanelComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<pp-assistant-panel class="pp-ai-designer" />`,
  styles: [
    `
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
