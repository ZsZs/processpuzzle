import { TemplatePortal } from '@angular/cdk/portal';
import { HttpErrorResponse } from '@angular/common/http';
import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, DestroyRef, effect, inject, input, output, signal, TemplateRef, untracked, viewChild, ViewContainerRef } from '@angular/core';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatDivider } from '@angular/material/divider';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuContent, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { ActivatedRoute, Router } from '@angular/router';
import { provideTranslocoScope, TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LayoutService } from '@processpuzzle/util';
import { SidenavContentService, TreeNavComponent, TreeNavNode, TreeNavNodeActionsDirective } from '@processpuzzle/widgets';
import { firstValueFrom } from 'rxjs';
import { BASE_DOCUMENT_TRANSLOCO_SCOPE } from '../../base-document.i18n';
import { Document } from '../../domain/base-document';
import { BaseDocumentService } from '../../domain/base-document.service';
import { ContentBook, ContentBookNode } from '../../domain/content-book';
import { contentOf, ContentBookService } from '../../domain/content-book.service';
import { DOCUMENT_ACCESS_CONTEXT, mayEdit } from '../../domain/document-access-context';
import { AddDocumentDialog, AddDocumentDialogData } from './add-document.dialog';
import { ConfirmDeleteDocumentDialog } from './confirm-delete-document.dialog';
import { ContentBookDocumentViewComponent } from './content-book-document-view.component';
import { addNode, canIndent, canMove, canOutdent, detachDocument, indent, move, MoveDirection, outdent, removeSubtree, toTreeNav } from './content-book-tree';

/** The query parameter that names the open page, by document slug — so a link to a page of a book can be shared. */
export const CONTENT_BOOK_PAGE_PARAM = 'doc';

/**
 * A content book, read and arranged in place: its table of contents as a tree, and the selected page beside it.
 *
 * **The tree takes the sidenav over** when there is one — the book's contents are the navigation a reader of a
 * book wants — and the sidenav's own toggle brings the menu back. Without a sidenav (an application whose layout
 * has none, a phone, or `sidenav` turned off) the tree stands in a column of its own.
 *
 * **Addressed by slug**, so a seeded book is addressable before anyone knows its generated id; the open page is
 * the `doc` query parameter, a document slug for the same reason.
 *
 * Editors of the book get the arranging verbs on every row; whether to *offer* them is decided from
 * {@link DOCUMENT_ACCESS_CONTEXT}, whether they are *allowed* by the backend.
 */
@Component({
  selector: 'pp-content-book',
  imports: [
    NgTemplateOutlet,
    TreeNavComponent,
    TreeNavNodeActionsDirective,
    ContentBookDocumentViewComponent,
    MatIconButton,
    MatButton,
    MatIcon,
    MatMenu,
    MatMenuContent,
    MatMenuItem,
    MatMenuTrigger,
    MatDivider,
    TranslocoPipe,
  ],
  providers: [provideTranslocoScope({ scope: BASE_DOCUMENT_TRANSLOCO_SCOPE, alias: BASE_DOCUMENT_TRANSLOCO_SCOPE })],
  template: `
    <ng-template #contents>
      <div class="pp-content-book__toc" data-testid="content-book-toc">
        @if (canEdit()) {
          <div class="pp-content-book__toc-actions">
            <button mat-button type="button" data-testid="content-book-add-document" (click)="addDocument(null)">
              <mat-icon>add</mat-icon>
              {{ 'base_document.content_book.widget.add_document' | transloco }}
            </button>
          </div>
        }
        @if (treeNodes().length === 0) {
          <p class="pp-content-book__hint">{{ 'base_document.content_book.widget.empty' | transloco }}</p>
        }
        <pp-tree-nav [nodes]="treeNodes()" [selectedId]="selectedNodeId()" [expandAll]="true" (selected)="select($event.id)">
          @if (canEdit()) {
            <ng-template ppTreeNavNodeActions let-node>
              <button
                mat-icon-button
                type="button"
                class="pp-content-book__node-menu"
                [attr.data-testid]="'content-book-node-actions-' + node.id"
                [attr.aria-label]="'base_document.content_book.widget.actions' | transloco"
                [matMenuTriggerFor]="nodeMenu"
                [matMenuTriggerData]="{ node }"
              >
                <mat-icon>more_vert</mat-icon>
              </button>
            </ng-template>
          }
        </pp-tree-nav>
        @if (actionError(); as error) {
          <p role="alert" class="pp-content-book__error">{{ error }}</p>
        }
      </div>
    </ng-template>

    <mat-menu #nodeMenu>
      <ng-template matMenuContent let-node="node">
        <button mat-menu-item type="button" data-testid="content-book-add-child" (click)="addDocument(node.id)">
          <mat-icon>subdirectory_arrow_right</mat-icon>{{ 'base_document.content_book.widget.add_child' | transloco }}
        </button>
        <mat-divider />
        <button mat-menu-item type="button" data-testid="content-book-move-up" [disabled]="!can('up', node.id)" (click)="moveNode(node.id, 'up')">
          <mat-icon>arrow_upward</mat-icon>{{ 'base_document.content_book.widget.move_up' | transloco }}
        </button>
        <button mat-menu-item type="button" data-testid="content-book-move-down" [disabled]="!can('down', node.id)" (click)="moveNode(node.id, 'down')">
          <mat-icon>arrow_downward</mat-icon>{{ 'base_document.content_book.widget.move_down' | transloco }}
        </button>
        <button mat-menu-item type="button" data-testid="content-book-indent" [disabled]="!can('indent', node.id)" (click)="indentNode(node.id)">
          <mat-icon>format_indent_increase</mat-icon>{{ 'base_document.content_book.widget.indent' | transloco }}
        </button>
        <button mat-menu-item type="button" data-testid="content-book-outdent" [disabled]="!can('outdent', node.id)" (click)="outdentNode(node.id)">
          <mat-icon>format_indent_decrease</mat-icon>{{ 'base_document.content_book.widget.outdent' | transloco }}
        </button>
        <mat-divider />
        <button mat-menu-item type="button" data-testid="content-book-remove" (click)="removeNode(node.id)">
          <mat-icon>playlist_remove</mat-icon>{{ 'base_document.content_book.widget.remove' | transloco }}
        </button>
        <button mat-menu-item type="button" data-testid="content-book-delete-document" (click)="deleteDocument(node.id)">
          <mat-icon>delete</mat-icon>{{ 'base_document.content_book.widget.delete_document' | transloco }}
        </button>
      </ng-template>
    </mat-menu>

    @if (loading()) {
      <p class="pp-content-book__hint">{{ 'base_document.content_book.widget.loading' | transloco }}</p>
    } @else if (loadError(); as error) {
      <p role="alert" class="pp-content-book__error" data-testid="content-book-error">{{ error }}</p>
    } @else if (book()) {
      <div class="pp-content-book" [class.pp-content-book--inline]="!inSidenav()">
        @if (!inSidenav()) {
          <aside class="pp-content-book__aside">
            <h3 class="pp-content-book__title">{{ book()?.title }}</h3>
            <ng-container *ngTemplateOutlet="contents" />
          </aside>
        }
        <article class="pp-content-book__page" data-testid="content-book-page">
          @if (selectedNode(); as node) {
            <pp-content-book-document-view [documentId]="node.documentId" [locale]="effectiveLocale()" [document]="documents().get(node.documentId)" />
          }
        </article>
      </div>
    }
  `,
  styles: `
    :host {
      display: block;
    }
    .pp-content-book--inline {
      display: grid;
      gap: 16px;
      grid-template-columns: minmax(200px, 280px) 1fr;
    }
    .pp-content-book__title {
      margin: 0 0 8px;
    }
    .pp-content-book__page {
      background-color: var(--pp-surface-base, #ffffff);
      border-radius: 6px;
      min-width: 0;
      padding: 16px 20px 24px;
    }
    .pp-content-book__toc-actions {
      display: flex;
      justify-content: flex-end;
    }
    .pp-content-book__node-menu {
      --mat-icon-button-state-layer-size: 32px;
      height: 32px;
      padding: 4px;
      width: 32px;
    }
    .pp-content-book__hint {
      opacity: 0.8;
      padding-inline: 12px;
    }
    .pp-content-book__error {
      color: var(--mat-sys-error, #b3261e);
      padding-inline: 12px;
    }
  `,
})
export class ContentBookWidgetComponent {
  /** The book to show, by slug. */
  readonly bookSlug = input.required<string>();
  /** The language pages are read in; the application's active language when left out. */
  readonly locale = input<string | undefined>(undefined);
  /** Whether the contents may take the application's sidenav over. */
  readonly sidenav = input(true);
  /** The page opened first, when the address names none; the book's first page when this is left out too. */
  readonly startDocumentSlug = input<string | undefined>(undefined);
  /** The slug of every page the reader opens. */
  readonly documentSelected = output<string>();

  private readonly bookService = inject(ContentBookService);
  private readonly documentService = inject(BaseDocumentService);
  private readonly sidenavContent = inject(SidenavContentService);
  private readonly layout = inject(LayoutService);
  private readonly transloco = inject(TranslocoService);
  private readonly dialog = inject(MatDialog);
  private readonly accessContext = inject(DOCUMENT_ACCESS_CONTEXT, { optional: true });
  private readonly router = inject(Router, { optional: true });
  private readonly route = inject(ActivatedRoute, { optional: true });
  private readonly viewContainerRef = inject(ViewContainerRef);

  private readonly contentsTemplate = viewChild.required<TemplateRef<unknown>>('contents');

  protected readonly book = signal<ContentBook | undefined>(undefined);
  protected readonly documents = signal(new Map<string, Document>());
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | undefined>(undefined);
  protected readonly actionError = signal<string | undefined>(undefined);
  protected readonly selectedNodeId = signal<string | undefined>(undefined);

  protected readonly canEdit = computed(() => {
    const book = this.book();
    return book !== undefined && mayEdit(this.accessContext, book.editorRoles);
  });
  protected readonly effectiveLocale = computed(() => this.locale() || this.transloco.getActiveLang());
  protected readonly inSidenav = computed(() => this.sidenav() && this.sidenavContent.hasOutlet() && !this.layout.isSmallDevice());
  protected readonly treeNodes = computed<TreeNavNode[]>(() => {
    const restricted = this.transloco.translate('base_document.content_book.widget.restricted');
    const documents = this.documents();
    return toTreeNav(this.book()?.nodes ?? [], (documentId) => {
      const document = documents.get(documentId);
      return document ? { label: document.title, icon: 'description' } : { label: restricted, icon: 'lock' };
    });
  });
  protected readonly selectedNode = computed(() => this.book()?.nodes.find((node) => node.id === this.selectedNodeId()));

  constructor() {
    effect(() => {
      const slug = this.bookSlug();
      untracked(() => void this.load(slug));
    });

    // The contents go into the sidenav while there is one to show them, and come back out the moment there is
    // not — a phone-sized window, or a layout without a sidenav.
    let detach: (() => void) | undefined;
    effect(() => {
      const show = this.inSidenav() && this.book() !== undefined;
      const title = this.book()?.title ?? '';
      untracked(() => {
        detach?.();
        detach = show ? this.sidenavContent.attach(new TemplatePortal(this.contentsTemplate(), this.viewContainerRef), title) : undefined;
      });
    });
    inject(DestroyRef).onDestroy(() => detach?.());
  }

  protected select(nodeId: string): void {
    this.selectedNodeId.set(nodeId);
    const slug = this.slugOfNode(nodeId);
    if (!slug) return;

    this.documentSelected.emit(slug);
    void this.router?.navigate([], { relativeTo: this.route, queryParams: { [CONTENT_BOOK_PAGE_PARAM]: slug }, queryParamsHandling: 'merge', replaceUrl: true });
  }

  protected can(action: MoveDirection | 'indent' | 'outdent', nodeId: string): boolean {
    const nodes = this.book()?.nodes ?? [];
    if (action === 'indent') return canIndent(nodes, nodeId);
    if (action === 'outdent') return canOutdent(nodes, nodeId);
    return canMove(nodes, nodeId, action);
  }

  protected moveNode(nodeId: string, direction: MoveDirection): Promise<void> {
    return this.saveTree((nodes) => move(nodes, nodeId, direction));
  }

  protected indentNode(nodeId: string): Promise<void> {
    return this.saveTree((nodes) => indent(nodes, nodeId));
  }

  protected outdentNode(nodeId: string): Promise<void> {
    return this.saveTree((nodes) => outdent(nodes, nodeId));
  }

  protected removeNode(nodeId: string): Promise<void> {
    return this.saveTree((nodes) => removeSubtree(nodes, nodeId));
  }

  protected async addDocument(parentId: string | null): Promise<void> {
    const book = this.book();
    if (!book) return;

    const data: AddDocumentDialogData = {
      locale: this.effectiveLocale(),
      isPublic: book.isPublic,
      readerRoles: book.readerRoles,
      editorRoles: book.editorRoles,
      excludeDocumentIds: book.nodes.map((node) => node.documentId),
    };
    const document = await firstValueFrom(this.dialog.open<AddDocumentDialog, AddDocumentDialogData, Document>(AddDocumentDialog, { data }).afterClosed());
    if (!document?.id) return;

    const documentId = document.id;
    this.documents.update((documents) => new Map(documents).set(documentId, document));
    await this.saveTree((nodes) => addNode(nodes, documentId, parentId));
    const added = this.book()?.nodes.find((node) => node.documentId === documentId);
    if (added) this.select(added.id);
  }

  /** Deletes the document itself, after asking; the server takes it out of every book, and this tree follows. */
  protected async deleteDocument(nodeId: string): Promise<void> {
    const node = this.book()?.nodes.find((each) => each.id === nodeId);
    if (!node) return;

    const title = this.documents().get(node.documentId)?.title ?? node.documentId;
    const confirmed = await firstValueFrom(this.dialog.open(ConfirmDeleteDocumentDialog, { data: { title } }).afterClosed());
    if (!confirmed) return;

    this.actionError.set(undefined);
    try {
      await firstValueFrom(this.documentService.delete(node.documentId));
      this.book.update((book) => (book ? { ...book, nodes: detachDocument(book.nodes, node.documentId) } : book));
      if (this.selectedNodeId() === nodeId) this.selectedNodeId.set(this.book()?.nodes[0]?.id);
    } catch (error) {
      this.actionError.set(this.transloco.translate('base_document.content_book.widget.save_error', { message: messageOf(error) }));
    }
  }

  private async saveTree(edit: (nodes: ContentBookNode[]) => ContentBookNode[]): Promise<void> {
    const book = this.book();
    if (!book?.id) return;

    this.actionError.set(undefined);
    try {
      this.book.set(await this.bookService.replaceNodes(book.id, edit(book.nodes)));
    } catch (error) {
      this.actionError.set(this.transloco.translate('base_document.content_book.widget.save_error', { message: messageOf(error) }));
    }
  }

  private async load(slug: string): Promise<void> {
    this.loading.set(true);
    this.loadError.set(undefined);
    try {
      const book = await this.bookService.findBySlug(slug);
      if (!book) {
        this.book.set(undefined);
        this.loadError.set(this.transloco.translate('base_document.content_book.widget.not_found', { slug }));
        return;
      }
      this.documents.set(await this.loadDocuments(book.nodes.map((node) => node.documentId)));
      this.book.set(book);
      this.selectedNodeId.set(this.initialNodeId(book));
    } catch (error) {
      this.loadError.set(this.transloco.translate('base_document.content_book.widget.load_error', { message: messageOf(error) }));
    } finally {
      this.loading.set(false);
    }
  }

  /** The book's documents in one listing call. One the reader may not see is left out by the server, and shown as restricted. */
  private async loadDocuments(ids: string[]): Promise<Map<string, Document>> {
    if (ids.length === 0) return new Map();
    const listed = contentOf(await firstValueFrom(this.documentService.findByQuery({ query: `id=in=(${ids.join(',')})`, pageSize: ids.length })));
    return new Map(listed.map((document) => [document.id as string, document]));
  }

  private initialNodeId(book: ContentBook): string | undefined {
    const wanted = this.route?.snapshot.queryParamMap.get(CONTENT_BOOK_PAGE_PARAM) ?? this.startDocumentSlug();
    const bySlug = wanted ? book.nodes.find((node) => this.documents().get(node.documentId)?.slug === wanted) : undefined;
    return (bySlug ?? book.nodes[0])?.id;
  }

  private slugOfNode(nodeId: string): string | undefined {
    const node = this.book()?.nodes.find((each) => each.id === nodeId);
    return node ? this.documents().get(node.documentId)?.slug : undefined;
  }
}

function messageOf(error: unknown): string {
  if (error instanceof HttpErrorResponse) return error.error?.errorText ?? error.message;
  return error instanceof Error ? error.message : String(error);
}
