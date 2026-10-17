import { Component, computed, inject, input, OnChanges } from '@angular/core';
import { WidgetHostComponent, WidgetPortEvent } from '@processpuzzle/widgets';
import { BlockKind, DocumentBlock, WidgetPlacement } from '../../domain/base-document';
import { DocumentContentStore } from '../document-editor/document-content.store';
import { DocumentTextBlockComponent } from '../document-editor/document-text-block.component';

/**
 * Published content, read-only — what a signer reads before signing. Renders the block list the way
 * `DocumentEditorComponent` does, through the same text-block and widget-host components, so a document
 * reads here exactly as its author laid it out; it only leaves out the append actions.
 *
 * **Read-only by construction, not by hiding buttons.** Every block is handed to the text block with
 * `editable: false`, which creates its Tiptap editor non-editable — so no update ever fires and the store's
 * debounced save is never reached. The store is provided here only because the text block resolves embedded
 * widgets through it.
 */
@Component({
  selector: 'pp-document-reader',
  standalone: true,
  imports: [DocumentTextBlockComponent, WidgetHostComponent],
  providers: [DocumentContentStore],
  template: `
    <div class="pp-document-reader" data-testid="document-reader">
      @for (block of standaloneBlocks(); track block.id) {
        @switch (block.kind) {
          @case (blockKindText) {
            <pp-document-text-block [block]="block" />
          }
          @case (blockKindWidget) {
            <pp-widget-host [widget]="block" [bindingResolver]="resolveBinding" (portEmit)="onPortEmit($event)" />
          }
        }
      }
    </div>
  `,
})
export class DocumentReaderComponent implements OnChanges {
  readonly documentId = input.required<string>();
  readonly locale = input.required<string>();
  readonly blocks = input.required<DocumentBlock[]>();

  private readonly contentStore = inject(DocumentContentStore);

  protected readonly blockKindText = BlockKind.TEXT;
  protected readonly blockKindWidget = BlockKind.WIDGET;
  protected readonly standaloneBlocks = computed(() => this.contentStore.blocks().filter((b) => b.kind !== BlockKind.WIDGET || b.placement !== WidgetPlacement.REFERENCED));
  protected readonly resolveBinding = (portName: string): unknown => this.contentStore.resolveBinding(portName);

  ngOnChanges(): void {
    const readOnly = this.blocks().map((block) => ({ ...block, editable: false }));
    this.contentStore.initialize(this.documentId(), this.locale(), readOnly);
  }

  protected onPortEmit(event: WidgetPortEvent): void {
    this.contentStore.publishOutput(event.port, event.value);
  }
}
