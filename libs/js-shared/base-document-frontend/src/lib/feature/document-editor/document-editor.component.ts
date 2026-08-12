import { CommonModule } from '@angular/common';
import { Component, computed, inject, input, OnChanges, signal, Type } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { TranslocoPipe } from '@jsverse/transloco';
import { WIDGET_REGISTRY } from '@processpuzzle/base-entity';
import { DocumentBlock, BlockKind, WidgetPlacement } from '../../domain/base-document';
import { DocumentContentStore } from './document-content.store';
import { DocumentTextBlockComponent } from './document-text-block.component';

/**
 * The primary editing surface for a document's content — see BaseDocumentContainerComponent
 * for how this sits alongside the generic Properties form on one screen. Renders the flat
 * block list in order: TEXT blocks get their own DocumentTextBlockComponent/Editor instance,
 * STANDALONE WIDGET blocks mount directly via NgComponentOutlet. REFERENCED WIDGET blocks are
 * deliberately absent from this top-level list — they only ever render inside whichever TEXT
 * block's Tiptap content embeds them via widgetEmbed (see DocumentTextBlockComponent), which is
 * the one place base-document-api.yaml lets a REFERENCED block appear.
 */
@Component({
  selector: 'pp-document-editor',
  standalone: true,
  imports: [CommonModule, DocumentTextBlockComponent, MatButton, MatIcon, MatMenu, MatMenuItem, MatMenuTrigger, TranslocoPipe],
  providers: [DocumentContentStore],
  template: `
    <div class="pp-document-editor">
      @for (block of standaloneBlocks(); track block.id) {
        @switch (block.kind) {
          @case (blockKindText) {
            <pp-document-text-block [block]="block" />
          }
          @case (blockKindWidget) {
            <ng-container *ngComponentOutlet="componentFor(block); inputs: propsFor(block)" />
          }
        }
      }

      <div class="pp-document-editor__actions">
        <button mat-stroked-button type="button" data-testid="document-add-text-block" [disabled]="isAppending()" (click)="onAddTextBlock()">
          <mat-icon>add</mat-icon>
          {{ 'base_document.document.content.add_text_block' | transloco }}
        </button>
        @if (widgetTypes().length > 0) {
          <button mat-stroked-button type="button" data-testid="document-add-widget-block" [disabled]="isAppending()" [matMenuTriggerFor]="widgetMenu">
            <mat-icon>widgets</mat-icon>
            {{ 'base_document.document.content.add_widget_block' | transloco }}
          </button>
          <mat-menu #widgetMenu>
            @for (widgetType of widgetTypes(); track widgetType) {
              <button mat-menu-item type="button" (click)="onAddWidgetBlock(widgetType)">{{ widgetType }}</button>
            }
          </mat-menu>
        }
      </div>

      @if (appendError()) {
        <p class="pp-document-editor__error" role="alert">{{ appendError() }}</p>
      }
    </div>
  `,
  styles: [
    `
      .pp-document-editor__actions {
        display: flex;
        gap: 8px;
        margin-top: 16px;
      }
    `,
  ],
})
export class DocumentEditorComponent implements OnChanges {
  readonly documentId = input.required<string>();
  /** Locale whose draft is edited. Blocks are stored per locale — see DocumentContentService. */
  readonly locale = input.required<string>();
  readonly blocks = input.required<DocumentBlock[]>();
  /** False when the locale has no draft yet, so the first append creates one. See DocumentContentStore. */
  readonly translationExists = input<boolean>(true);

  private readonly widgetRegistry = inject(WIDGET_REGISTRY, { optional: true }) ?? new Map<string, Type<unknown>>();
  private readonly contentStore = inject(DocumentContentStore);

  protected readonly blockKindText = BlockKind.TEXT;
  protected readonly blockKindWidget = BlockKind.WIDGET;
  protected readonly isAppending = signal(false);
  protected readonly appendError = signal<string | undefined>(undefined);
  protected readonly widgetTypes = computed(() => [...this.widgetRegistry.keys()]);

  /**
   * Re-seeds the content store whenever a different document is loaded — cheap, since
   * DocumentContentStore.initialize() is just two signal writes.
   *
   * A lifecycle hook rather than a computed because `initialize()` writes signals, which a reactive
   * computation may not do (NG0600), and rather than an `effect()` because a view effect runs after
   * the template of the pass that registered it (`refreshView` calls `executeTemplate` before
   * `runEffectsInView`) — the blocks would render empty once and only appear on the dirty re-run.
   * `ngOnChanges` is a pre-order hook of the *parent* view, so it lands before this component's
   * template is first executed, and unlike `ngOnInit` it runs again when the container swaps a
   * different document into the same editor instance.
   */
  ngOnChanges(): void {
    this.contentStore.initialize(this.documentId(), this.locale(), this.blocks(), this.translationExists());
  }

  // Renders from the store's own live signal (not the `blocks` input directly), so edits made
  // through DocumentContentStore — append/delete/reorder — are reflected without the parent
  // needing to re-fetch the whole document.
  protected readonly standaloneBlocks = computed(() => this.contentStore.blocks().filter((b) => b.kind !== BlockKind.WIDGET || b.placement !== WidgetPlacement.REFERENCED));

  protected componentFor(block: DocumentBlock): Type<unknown> | null {
    return block.type ? this.widgetRegistry.get(block.type) ?? null : null;
  }

  protected propsFor(block: DocumentBlock): Record<string, unknown> {
    // Same resolution rule as WidgetEmbedNodeView.applyProps: a declared input binding wins
    // over the matching static prop. Kept intentionally duplicated rather than shared, since
    // one is plain-object-in-a-template and the other runs inside a ProseMirror NodeView with
    // no template binding mechanism at all — a shared helper would need to abstract over that
    // difference for no real benefit at this size.
    const resolved: Record<string, unknown> = { ...block.props };
    for (const [widgetPropName, portName] of Object.entries(block.inputBindings ?? {})) {
      resolved[widgetPropName] = this.contentStore.resolveBinding(portName);
    }
    return resolved;
  }

  /**
   * The one path by which a document that has no content can get some — without it the editor is a dead
   * end for every document whose locale has never been written, which is every newly created one.
   */
  async onAddTextBlock(): Promise<void> {
    await this.append(() => this.contentStore.appendTextBlock());
  }

  async onAddWidgetBlock(widgetType: string): Promise<void> {
    await this.append(() => this.contentStore.appendStandaloneWidget(widgetType));
  }

  /**
   * Surfaced in the editor rather than left to the global HTTP error snackbar: appending is the action the
   * user just took here, and a 403 on a document they may not edit has to be visible next to the button
   * that failed. Guarded against a second concurrent append, which would race two POSTs onto the same draft.
   */
  private async append(appendBlock: () => Promise<string>): Promise<void> {
    if (this.isAppending()) return;

    this.isAppending.set(true);
    this.appendError.set(undefined);
    try {
      await appendBlock();
    } catch (error) {
      this.appendError.set(describeError(error));
    } finally {
      this.isAppending.set(false);
    }
  }

  /** Called by a toolbar action to embed a new widget at the given text block's cursor. */
  async insertWidgetInto(textBlock: DocumentTextBlockComponent, widgetType: string, props: Record<string, unknown> = {}) {
    const blockId = await this.contentStore.appendReferencedWidget(widgetType, props);
    textBlock.embedWidget(blockId);
  }
}

/**
 * What went wrong, in the one line the alert has room for. A failed append is almost always an
 * `HttpErrorResponse`, which carries a `message` but is not an `Error` — so testing for `Error` alone left
 * the user reading `[object Object]` in place of the status the request came back with.
 */
function describeError(error: unknown): string {
  if (typeof error === 'object' && error !== null && 'message' in error) return String((error as { message: unknown }).message);
  return String(error);
}
