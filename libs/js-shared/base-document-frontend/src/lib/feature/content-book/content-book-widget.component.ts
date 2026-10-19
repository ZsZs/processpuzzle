import { TemplatePortal } from '@angular/cdk/portal';
import { HttpErrorResponse } from '@angular/common/http';
import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, DestroyRef, effect, inject, Injector, input, output, signal, TemplateRef, untracked, viewChild, ViewContainerRef } from '@angular/core';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatDivider } from '@angular/material/divider';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuContent, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { MatTooltip } from '@angular/material/tooltip';
import { ActivatedRoute, Router } from '@angular/router';
import { provideTranslocoScope, TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { BaseFormNavigatorSingletonStore } from '@processpuzzle/base-entity';
import { LayoutService } from '@processpuzzle/util';
import { SidenavContentService, TreeNavComponent, TreeNavNode, TreeNavNodeActionsDirective } from '@processpuzzle/widgets';
import { firstValueFrom } from 'rxjs';
import { BASE_DOCUMENT_TRANSLOCO_SCOPE } from '../../base-document.i18n';
import { Document, DocumentStatus, DocumentTranslationSummary } from '../../domain/base-document';
import { BaseDocumentService } from '../../domain/base-document.service';
import { ContentBook, ContentBookNode } from '../../domain/content-book';
import { contentOf, ContentBookService } from '../../domain/content-book.service';
import { DOCUMENT_ACCESS_CONTEXT, mayEdit } from '../../domain/document-access-context';
import { DOCUMENT_ENTITY_NAME } from '../../domain/document-entity-names';
import { AddDocumentDialog, AddDocumentDialogData } from './add-document.dialog';
import { ConfirmArchiveDocumentDialog } from './confirm-archive-document.dialog';
import { ConfirmDeleteDocumentDialog } from './confirm-delete-document.dialog';
import { ContentBookDocumentViewComponent } from './content-book-document-view.component';
import {
  addNode,
  canIndent,
  canMove,
  canOutdent,
  detachDocument,
  indent,
  insertBeside,
  move,
  MoveDirection,
  outdent,
  removeSubtree,
  toTreeNav,
} from './content-book-tree';

/** The query parameter that names the open page, by document slug — so a link to a page of a book can be shared. */
export const CONTENT_BOOK_PAGE_PARAM = 'doc';

/** Where the toolbar's Add files the new document: beside the selected page, below it, or at the top level. */
export type ContentBookAddPosition = 'before' | 'after' | 'child' | 'root';

/**
 * A content book, read and arranged in place: its table of contents as a tree, the selected page beside it, and a
 * toolbar over both that acts on the selected page.
 *
 * **The tree takes the sidenav over** when there is one — the book's contents are the navigation a reader of a
 * book wants — and the sidenav's own toggle brings the menu back. Without a sidenav (an application whose layout
 * has none, a phone, or `sidenav` turned off) the tree stands in a column of its own.
 *
 * **Addressed by slug**, so a seeded book is addressable before anyone knows its generated id; the open page is
 * the `doc` query parameter, a document slug for the same reason.
 *
 * **The toolbar** — Add, Edit, Publish, Archive, Properties — is offered to editors of the book; whether a verb
 * is *allowed* on a page is the page's own roles' business, and finally the backend's. Editing a published page
 * edits its draft, so readers keep the published revision until the next Publish. An archived page is
 * read-only. Readers see only published, unarchived pages; editors see every page unless they switch the
 * unpublished ones off.
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
    MatSlideToggle,
    MatTooltip,
    TranslocoPipe,
  ],
  providers: [provideTranslocoScope({ scope: BASE_DOCUMENT_TRANSLOCO_SCOPE, alias: BASE_DOCUMENT_TRANSLOCO_SCOPE })],
  templateUrl: './content-book-widget.component.html',
  styles: `
    :host {
      display: block;
    }
    .pp-content-book--inline {
      display: grid;
      gap: 16px;
      grid-template-columns: minmax(200px, 280px) 1fr;
    }
    .pp-content-book__toolbar {
      align-items: center;
      border-bottom: 1px solid var(--mat-sys-outline-variant, #c4c7c5);
      display: flex;
      flex-wrap: wrap;
      gap: 4px 8px;
      margin-bottom: 12px;
      padding-bottom: 4px;
    }
    .pp-content-book__title {
      margin: 0;
    }
    .pp-content-book__spacer {
      flex: 1 1 auto;
    }
    .pp-content-book__status {
      border-radius: 12px;
      font-size: 0.8125rem;
      padding: 2px 10px;
      white-space: nowrap;
      background: color-mix(in srgb, var(--mat-sys-primary, #186fce) 15%, transparent);
    }
    .pp-content-book__status--draft,
    .pp-content-book__status--changed {
      background: color-mix(in srgb, var(--mat-sys-tertiary, #b58100) 20%, transparent);
    }
    .pp-content-book__status--archived {
      background: color-mix(in srgb, var(--mat-sys-outline, #747775) 25%, transparent);
    }
    .pp-content-book__page {
      background-color: var(--pp-surface-base, #ffffff);
      border-radius: 6px;
      min-width: 0;
      padding: 16px 20px 24px;
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
  private readonly injector = inject(Injector);
  private readonly viewContainerRef = inject(ViewContainerRef);

  private readonly contentsTemplate = viewChild.required<TemplateRef<unknown>>('contents');
  protected readonly pageView = viewChild(ContentBookDocumentViewComponent);

  protected readonly book = signal<ContentBook | undefined>(undefined);
  protected readonly documents = signal(new Map<string, Document>());
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | undefined>(undefined);
  protected readonly actionError = signal<string | undefined>(undefined);
  protected readonly selectedNodeId = signal<string | undefined>(undefined);
  protected readonly showUnpublished = signal(true);
  protected readonly busy = signal(false);

  protected readonly canEdit = computed(() => {
    const book = this.book();
    return book !== undefined && mayEdit(this.accessContext, book.editorRoles);
  });
  protected readonly effectiveLocale = computed(() => this.locale() || this.transloco.getActiveLang());
  protected readonly inSidenav = computed(() => this.sidenav() && this.sidenavContent.hasOutlet() && !this.layout.isSmallDevice());

  /** The book's nodes as shown: pages not yet published, or archived, folded away unless an editor asks for them. */
  private readonly visibleNodes = computed(() => {
    const nodes = this.book()?.nodes ?? [];
    if (this.canEdit() && this.showUnpublished()) return nodes;
    const documents = this.documents();
    return nodes
      .map((node) => documents.get(node.documentId))
      .filter((document): document is Document => document !== undefined && !isReadable(document))
      .reduce((shown, document) => detachDocument(shown, document.id as string), nodes);
  });
  protected readonly treeNodes = computed<TreeNavNode[]>(() => {
    const restricted = this.transloco.translate('base_document.content_book.widget.restricted');
    const documents = this.documents();
    return toTreeNav(this.visibleNodes(), (documentId) => {
      const document = documents.get(documentId);
      if (!document) return { label: restricted, icon: 'lock' };
      if (document.archive) return { label: document.title, icon: 'inventory_2' };
      return { label: document.title, icon: isPublished(document) ? 'description' : 'edit_note' };
    });
  });
  protected readonly selectedNode = computed(() => this.book()?.nodes.find((node) => node.id === this.selectedNodeId()));
  private readonly selectedDocument = computed(() => {
    const node = this.selectedNode();
    return node ? this.documents().get(node.documentId) : undefined;
  });

  /** The translation the page shows: the reader's language, or the document's source language without one. */
  private readonly pageTranslation = computed<DocumentTranslationSummary | undefined>(() => {
    const document = this.selectedDocument();
    if (!document) return undefined;
    const translations = document.translations ?? [];
    return translations.find((each) => each.locale === this.effectiveLocale()) ?? translations.find((each) => each.locale === document.sourceLocale);
  });

  /** The status chip: publication state and revisions of the page's translation, or that the page is archived. */
  protected readonly pageState = computed<{ kind: 'archived' | 'draft' | 'changed' | 'published'; label: string } | undefined>(() => {
    const document = this.selectedDocument();
    const translation = this.pageTranslation();
    if (!document) return undefined;
    const key = 'base_document.content_book.widget.status.';
    if (document.archive) return { kind: 'archived', label: this.transloco.translate(key + 'archived') };
    if (!translation) return undefined;
    const params = { revision: translation.revision ?? 0, published: translation.publishedRevision ?? 0 };
    switch (translation.status) {
      case DocumentStatus.PUBLISHED:
        return { kind: 'published', label: this.transloco.translate(key + 'published', params) };
      case DocumentStatus.PUBLISHED_WITH_DRAFT_CHANGES:
        return { kind: 'changed', label: this.transloco.translate(key + 'changed', params) };
      default:
        return { kind: 'draft', label: this.transloco.translate(key + 'draft', params) };
    }
  });

  private readonly mayPublishPage = computed(() => {
    const document = this.selectedDocument();
    if (!document || document.archive) return false;
    return mayEdit(this.accessContext, document.publisherRoles.length > 0 ? document.publisherRoles : document.editorRoles);
  });
  protected readonly canPublishPage = computed(() => this.mayPublishPage() && this.pageTranslation() !== undefined && this.pageTranslation()?.status !== DocumentStatus.PUBLISHED);
  protected readonly canArchivePage = computed(() => this.mayPublishPage());

  /** The selected page's details form, where this application mounts the Documents screens; none where it does not. */
  protected readonly propertiesUrl = computed(() => {
    const id = this.selectedDocument()?.id;
    const base = id ? documentScreensUrl(this.route) : undefined;
    return base === undefined ? undefined : `${base}/document/${id}/details`;
  });

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

  /** Files a new or existing document relative to `nodeId` — the selected page when left out. */
  protected async addDocument(position: ContentBookAddPosition, nodeId: string | undefined = this.selectedNodeId()): Promise<void> {
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
    await this.saveTree((nodes) => {
      if (!nodeId || position === 'root') return addNode(nodes, documentId, null);
      if (position === 'child') return addNode(nodes, documentId, nodeId);
      return insertBeside(nodes, documentId, nodeId, position);
    });
    const added = this.book()?.nodes.find((node) => node.documentId === documentId);
    if (added) this.select(added.id);
  }

  /** Publishes the translation the page shows; readers see its current draft from now on. */
  protected publishPage(): Promise<void> {
    const document = this.selectedDocument();
    const locale = this.pageTranslation()?.locale;
    if (!document?.id || !locale) return Promise.resolve();
    const documentId = document.id;
    return this.act(async () => {
      await this.documentService.publishTranslation(documentId, locale);
      await this.refreshDocument(documentId);
    });
  }

  /** Archives the selected page, after asking: its snapshot is stored and it can no longer be edited. */
  protected async archivePage(): Promise<void> {
    const document = this.selectedDocument();
    if (!document?.id) return;
    const documentId = document.id;
    const confirmed = await firstValueFrom(this.dialog.open(ConfirmArchiveDocumentDialog, { data: { title: document.title } }).afterClosed());
    if (!confirmed) return;

    await this.act(async () => {
      const archived = await this.documentService.archive(documentId);
      this.documents.update((documents) => new Map(documents).set(documentId, archived));
    });
  }

  /** The page's details form; its Cancel and Save come back here, through the navigator's `returnTo`. */
  protected openProperties(): void {
    const url = this.propertiesUrl();
    if (!url) return;
    // Resolved on use rather than injected: the navigator reads the router as it is created, and a book hosted
    // without the Documents screens — or without a router — never needs it.
    void this.injector.get(BaseFormNavigatorSingletonStore).navigateToUrl(url);
  }

  /** Deletes the document itself, after asking; the server takes it out of every book, and this tree follows. */
  protected async deleteDocument(nodeId: string): Promise<void> {
    const node = this.book()?.nodes.find((each) => each.id === nodeId);
    if (!node) return;

    const title = this.documents().get(node.documentId)?.title ?? node.documentId;
    const confirmed = await firstValueFrom(this.dialog.open(ConfirmDeleteDocumentDialog, { data: { title } }).afterClosed());
    if (!confirmed) return;

    await this.act(async () => {
      await firstValueFrom(this.documentService.delete(node.documentId));
      this.book.update((book) => (book ? { ...book, nodes: detachDocument(book.nodes, node.documentId) } : book));
      if (this.selectedNodeId() === nodeId) this.selectedNodeId.set(this.book()?.nodes[0]?.id);
    });
  }

  private saveTree(edit: (nodes: ContentBookNode[]) => ContentBookNode[]): Promise<void> {
    const book = this.book();
    if (!book?.id) return Promise.resolve();
    const bookId = book.id;
    return this.act(async () => this.book.set(await this.bookService.replaceNodes(bookId, edit(book.nodes))));
  }

  /** Runs one toolbar or tree action, turning its failure into the message under the toolbar. */
  private async act(action: () => Promise<void>): Promise<void> {
    this.actionError.set(undefined);
    this.busy.set(true);
    try {
      await action();
    } catch (error) {
      this.actionError.set(this.transloco.translate('base_document.content_book.widget.save_error', { message: messageOf(error) }));
    } finally {
      this.busy.set(false);
    }
  }

  /** Reloads one document's listing entry — its translation states — after something changed them. */
  private async refreshDocument(documentId: string): Promise<void> {
    const refreshed = await this.loadDocuments([documentId]);
    const document = refreshed.get(documentId);
    if (document) this.documents.update((documents) => new Map(documents).set(documentId, document));
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
    const visible = this.visibleNodes();
    return (bySlug ?? visible[0] ?? book.nodes[0])?.id;
  }

  private slugOfNode(nodeId: string): string | undefined {
    const node = this.book()?.nodes.find((each) => each.id === nodeId);
    return node ? this.documents().get(node.documentId)?.slug : undefined;
  }
}

function isPublished(document: Document): boolean {
  return (document.translations ?? []).some((translation) => translation.status !== DocumentStatus.DRAFT);
}

/** What a reader of the book is shown: a published page that is not archived. */
function isReadable(document: Document): boolean {
  return !document.archive && isPublished(document);
}

/**
 * The URL of the route that mounts the Documents screens — the nearest ancestor of `route` with a child route
 * declaring `entityName: 'Document'`, which is what `BASE_DOCUMENT_ROUTES` puts beside a content book's own route.
 * `undefined` where the application mounts none, and the Properties button is then disabled.
 */
export function documentScreensUrl(route: ActivatedRoute | null | undefined): string | undefined {
  for (let current = route ?? null; current; current = current.parent) {
    if (current.routeConfig?.children?.some((child) => child.data?.['entityName'] === DOCUMENT_ENTITY_NAME)) {
      return '/' + current.pathFromRoot.flatMap((each) => each.snapshot.url.map((segment) => segment.path)).join('/');
    }
  }
  return undefined;
}

function messageOf(error: unknown): string {
  if (error instanceof HttpErrorResponse) return error.error?.errorText ?? error.message;
  return error instanceof Error ? error.message : String(error);
}
