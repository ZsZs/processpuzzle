import { HttpErrorResponse } from '@angular/common/http';
import { Component, input, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { LayoutService } from '@processpuzzle/util';
import { SidenavContentService } from '@processpuzzle/widgets';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Document, DocumentStatus } from '../../domain/base-document';
import { BaseDocumentService } from '../../domain/base-document.service';
import { ContentBook, ContentBookNode } from '../../domain/content-book';
import { ContentBookService } from '../../domain/content-book.service';
import { DOCUMENT_ACCESS_CONTEXT } from '../../domain/document-access-context';
import { ContentBookDocumentViewComponent } from './content-book-document-view.component';
import { ContentBookWidgetComponent, documentScreensUrl } from './content-book-widget.component';

@Component({ selector: 'pp-content-book-document-view', template: '<p data-testid="page-of">{{ documentId() }}</p>' })
class PageStubComponent {
  readonly documentId = input.required<string>();
  readonly locale = input.required<string>();
  readonly document = input<Document | undefined>(undefined);
}

/** The protected surface of the widget the specs drive directly. */
interface WidgetInternals {
  can(action: string, nodeId: string): boolean;
  select(nodeId: string): void;
  moveNode(nodeId: string, direction: 'up' | 'down'): Promise<void>;
  indentNode(nodeId: string): Promise<void>;
  outdentNode(nodeId: string): Promise<void>;
  removeNode(nodeId: string): Promise<void>;
  addDocument(position: 'before' | 'after' | 'child' | 'root', nodeId?: string): Promise<void>;
  deleteDocument(nodeId: string): Promise<void>;
  book: { (): ContentBook | undefined; set(value: ContentBook | undefined): void };
  actionError(): string | undefined;
  selectedNode(): ContentBookNode | undefined;
  selectedNodeId(): string | undefined;
  effectiveLocale(): string;
  publishPage(): Promise<void>;
  archivePage(): Promise<void>;
  openProperties(): void;
  showUnpublished: { (): boolean; set(value: boolean): void };
  treeNodes(): { id: string; icon?: string }[];
  pageState(): { kind: string; label: string } | undefined;
  canPublishPage(): boolean;
  canArchivePage(): boolean;
  propertiesUrl(): string | undefined;
}

const NODES: ContentBookNode[] = [
  { id: 'n-overview', documentId: 'd-overview', parentId: null, orderIndex: 0 },
  { id: 'n-details', documentId: 'd-details', parentId: 'n-overview', orderIndex: 0 },
  { id: 'n-notes', documentId: 'd-notes', parentId: null, orderIndex: 1 },
];

const book = (nodes = NODES): ContentBook => new ContentBook('b-1', 'org', 'handbook', 'Handbook', undefined, true, [], ['editor'], nodes);
const doc = (id: string, slug: string): Document => new Document(id, 'org', slug, slug.toUpperCase());

describe('ContentBookWidgetComponent', () => {
  let fixture: ComponentFixture<ContentBookWidgetComponent>;
  let bookService: { findBySlug: ReturnType<typeof vi.fn>; replaceNodes: ReturnType<typeof vi.fn> };
  let documentService: { findByQuery: ReturnType<typeof vi.fn>; delete: ReturnType<typeof vi.fn>; publishTranslation: ReturnType<typeof vi.fn>; archive: ReturnType<typeof vi.fn> };
  let dialog: { open: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };
  let roles: ReturnType<typeof signal<readonly string[]>>;
  let sidenavContent: SidenavContentService;
  let queryParams: Record<string, string>;
  let withoutRouter: boolean;
  let small: ReturnType<typeof signal<boolean>>;

  const byTestId = (id: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
  const component = (): WidgetInternals => fixture.componentInstance as unknown as WidgetInternals;
  const flush = async (): Promise<void> => {
    await fixture.whenStable();
    await new Promise<void>((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();
  };
  const clickMenuItem = async (nodeId: string, testId: string): Promise<void> => {
    byTestId(`content-book-node-actions-${nodeId}`)?.click();
    fixture.detectChanges();
    (globalThis.document.querySelector(`[data-testid="${testId}"]`) as HTMLButtonElement).click();
    await flush();
  };

  async function render(inputs: Record<string, unknown> = {}): Promise<void> {
    fixture = TestBed.createComponent(ContentBookWidgetComponent);
    fixture.componentRef.setInput('bookSlug', 'handbook');
    Object.entries(inputs).forEach(([name, value]) => fixture.componentRef.setInput(name, value));
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise<void>((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();
  }

  beforeEach(() => {
    queryParams = {};
    small = signal(false);
    withoutRouter = false;
    roles = signal<readonly string[]>(['editor']);
    bookService = { findBySlug: vi.fn().mockResolvedValue(book()), replaceNodes: vi.fn((_id: string, nodes: ContentBookNode[]) => Promise.resolve(book(nodes))) };
    documentService = {
      findByQuery: vi.fn(() => of({ content: [doc('d-overview', 'overview'), doc('d-details', 'details'), doc('d-notes', 'notes')], number: 0, size: 3, totalElements: 3, totalPages: 1 })),
      delete: vi.fn(() => of(undefined)),
      publishTranslation: vi.fn().mockResolvedValue({}),
      archive: vi.fn(),
    };
    dialog = { open: vi.fn() };
    router = { navigate: vi.fn().mockResolvedValue(true) };

    TestBed.configureTestingModule({
      providers: [
        provideTranslocoTesting({ translations: { en: {} } }),
        { provide: ContentBookService, useValue: bookService },
        { provide: BaseDocumentService, useValue: documentService },
        { provide: MatDialog, useValue: dialog },
        { provide: Router, useFactory: () => (withoutRouter ? null : router) },
        { provide: ActivatedRoute, useFactory: () => (withoutRouter ? null : { snapshot: { queryParamMap: convertToParamMap(queryParams) } }) },
        { provide: LayoutService, useValue: { isSmallDevice: small } },
        { provide: DOCUMENT_ACCESS_CONTEXT, useFactory: () => ({ roles }) },
      ],
    }).overrideComponent(ContentBookWidgetComponent, { remove: { imports: [ContentBookDocumentViewComponent] }, add: { imports: [PageStubComponent] } });
    sidenavContent = TestBed.inject(SidenavContentService);
  });

  it('loads the book by slug and its documents in one listing call', async () => {
    await render();

    expect(bookService.findBySlug).toHaveBeenCalledWith('handbook');
    expect(documentService.findByQuery).toHaveBeenCalledWith({ query: 'id=in=(d-overview,d-details,d-notes)', pageSize: 3 });
  });

  it('stands the contents in a column of their own when there is no sidenav, and opens the first page', async () => {
    await render();

    expect(byTestId('content-book-toc')).not.toBeNull();
    expect(byTestId('tree-nav-node-n-overview')?.textContent).toContain('OVERVIEW');
    expect(byTestId('page-of')?.textContent).toBe('d-overview');
    expect(sidenavContent.portal()).toBeUndefined();
  });

  it('takes the sidenav over when there is one, and gives it back when destroyed', async () => {
    sidenavContent.registerOutlet();
    await render();

    expect(sidenavContent.portal()).toBeDefined();
    expect(sidenavContent.title()).toBe('Handbook');
    expect(byTestId('content-book-toc')).toBeNull();

    fixture.destroy();
    expect(sidenavContent.portal()).toBeUndefined();
  });

  it('keeps the contents beside the page when told not to use the sidenav', async () => {
    sidenavContent.registerOutlet();
    await render({ sidenav: false });

    expect(sidenavContent.portal()).toBeUndefined();
    expect(byTestId('content-book-toc')).not.toBeNull();
  });

  it('opens the page the address names, then the start page, then the first', async () => {
    queryParams = { doc: 'notes' };
    await render({ startDocumentSlug: 'details' });
    expect(byTestId('page-of')?.textContent).toBe('d-notes');
  });

  it('opens the start page when the address names none', async () => {
    await render({ startDocumentSlug: 'details' });
    expect(byTestId('page-of')?.textContent).toBe('d-details');
  });

  it('reports a selected page and puts it in the address', async () => {
    await render();
    const selected: string[] = [];
    fixture.componentInstance.documentSelected.subscribe((slug) => selected.push(slug));

    (byTestId('tree-nav-node-n-notes')?.querySelector('.pp-tree-nav__label') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(selected).toEqual(['notes']);
    expect(byTestId('page-of')?.textContent).toBe('d-notes');
    expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { doc: 'notes' }, queryParamsHandling: 'merge' }));
  });

  it('shows a page the reader may not see as restricted', async () => {
    documentService.findByQuery.mockReturnValue(of({ content: [doc('d-overview', 'overview')], number: 0, size: 3, totalElements: 1, totalPages: 1 }));
    await render();

    expect(byTestId('tree-nav-node-n-notes')?.querySelector('mat-icon.pp-tree-nav__icon')?.textContent).toBe('lock');
  });

  it('says so when there is no such book', async () => {
    bookService.findBySlug.mockResolvedValue(undefined);
    await render();

    expect(byTestId('content-book-error')).not.toBeNull();
  });

  it('says so when the book cannot be loaded', async () => {
    bookService.findBySlug.mockRejectedValue(new Error('boom'));
    await render();

    expect(byTestId('content-book-error')).not.toBeNull();
  });

  it('offers editors the arranging verbs, and readers none', async () => {
    await render();
    expect(byTestId('content-book-add-document')).not.toBeNull();
    expect(byTestId('content-book-node-actions-n-overview')).not.toBeNull();

    roles.set(['reader']);
    fixture.detectChanges();
    expect(byTestId('content-book-add-document')).toBeNull();
    expect(byTestId('content-book-node-actions-n-overview')).toBeNull();
  });

  it('saves every arrangement as the whole tree', async () => {
    await render();

    expect(component().can('down', 'n-overview')).toBe(true);
    expect(component().can('indent', 'n-notes')).toBe(true);
    expect(component().can('outdent', 'n-details')).toBe(true);

    await component().moveNode('n-overview', 'down');
    expect(bookService.replaceNodes).toHaveBeenLastCalledWith('b-1', expect.arrayContaining([expect.objectContaining({ id: 'n-notes', orderIndex: 0 })]));

    await component().indentNode('n-overview');
    await component().outdentNode('n-details');
    await component().removeNode('n-notes');
    expect(bookService.replaceNodes).toHaveBeenCalledTimes(4);
    expect(component().book()?.nodes.map((node: ContentBookNode) => node.id)).not.toContain('n-notes');
  });

  it('shows a refused save without losing the tree', async () => {
    await render();
    bookService.replaceNodes.mockRejectedValue(new Error('403'));

    await component().moveNode('n-overview', 'down');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.pp-content-book__error')).not.toBeNull();
    expect(component().book()?.nodes).toEqual(NODES);
  });

  it('files the document the dialog picked, and opens it', async () => {
    await render();
    const added = doc('d-new', 'new-page');
    dialog.open.mockReturnValue({ afterClosed: () => of(added) });

    await component().addDocument('child', 'n-overview');

    expect(dialog.open.mock.calls[0][1].data).toEqual(expect.objectContaining({ isPublic: true, editorRoles: ['editor'], excludeDocumentIds: ['d-overview', 'd-details', 'd-notes'] }));
    const saved = bookService.replaceNodes.mock.calls[0][1] as ContentBookNode[];
    expect(saved.find((node) => node.documentId === 'd-new')?.parentId).toBe('n-overview');
    expect(component().selectedNode()?.documentId).toBe('d-new');
  });

  it('files nothing when the dialog is cancelled', async () => {
    await render();
    dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });

    await component().addDocument('root');

    expect(bookService.replaceNodes).not.toHaveBeenCalled();
  });

  it('deletes a document after asking, and moves its pages up', async () => {
    await render();
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    component().select('n-overview');

    await component().deleteDocument('n-overview');

    expect(documentService.delete).toHaveBeenCalledWith('d-overview');
    expect(component().book()?.nodes.map((node: ContentBookNode) => `${node.parentId ?? '-'}/${node.id}`)).toEqual(['-/n-details', '-/n-notes']);
    expect(component().selectedNodeId()).toBe('n-details');
  });

  it('deletes nothing when the reader thinks better of it', async () => {
    await render();
    dialog.open.mockReturnValue({ afterClosed: () => of(false) });

    await component().deleteDocument('n-overview');

    expect(documentService.delete).not.toHaveBeenCalled();
  });

  it('opens a node’s menu and arranges through it', async () => {
    await render();

    byTestId('content-book-node-actions-n-overview')?.click();
    fixture.detectChanges();
    const moveUp = globalThis.document.querySelector('[data-testid="content-book-move-up"]') as HTMLButtonElement;
    const moveDown = globalThis.document.querySelector('[data-testid="content-book-move-down"]') as HTMLButtonElement;
    expect(moveUp.disabled).toBe(true);

    moveDown.click();
    await fixture.whenStable();

    expect(bookService.replaceNodes).toHaveBeenCalledTimes(1);
  });

  it('keeps the contents beside the page on a small device, and hands them to the sidenav again on a large one', async () => {
    sidenavContent.registerOutlet();
    small.set(true);
    await render();
    expect(sidenavContent.portal()).toBeUndefined();
    expect(byTestId('content-book-toc')).not.toBeNull();

    small.set(false);
    await flush();
    expect(sidenavContent.portal()).toBeDefined();
  });

  it('reads in the given locale, else the active language', async () => {
    await render({ locale: 'de' });
    expect(component().effectiveLocale()).toBe('de');

    await render();
    expect(component().effectiveLocale()).toBe('en');
  });

  it('opens the first page when the address names an unknown one', async () => {
    queryParams = { doc: 'nowhere' };
    await render();
    expect(component().selectedNodeId()).toBe('n-overview');
  });

  it('hints at an empty book, and loads no documents', async () => {
    bookService.findBySlug.mockResolvedValue(book([]));
    await render();

    expect(documentService.findByQuery).not.toHaveBeenCalled();
    expect(byTestId('content-book-toc')?.querySelector('.pp-content-book__hint')).not.toBeNull();
    expect(component().selectedNode()).toBeUndefined();
  });

  it('selects a restricted page without reporting or navigating', async () => {
    documentService.findByQuery.mockReturnValue(of({ content: [doc('d-overview', 'overview')], number: 0, size: 3, totalElements: 1, totalPages: 1 }));
    await render();
    const selected: string[] = [];
    fixture.componentInstance.documentSelected.subscribe((slug) => selected.push(slug));

    component().select('n-notes');

    expect(selected).toEqual([]);
    expect(router.navigate).not.toHaveBeenCalled();
    expect(component().selectedNodeId()).toBe('n-notes');
  });

  it('works without a router', async () => {
    withoutRouter = true;
    await render();

    component().select('n-notes');

    expect(component().selectedNodeId()).toBe('n-notes');
  });

  it('runs every arranging verb from the node menu', async () => {
    await render();

    await clickMenuItem('n-notes', 'content-book-move-up');
    await clickMenuItem('n-notes', 'content-book-move-down');
    await clickMenuItem('n-notes', 'content-book-indent');
    await clickMenuItem('n-details', 'content-book-outdent');
    await clickMenuItem('n-notes', 'content-book-remove');

    expect(bookService.replaceNodes).toHaveBeenCalledTimes(5);
  });

  it('adds a child from the node menu and a page from the toolbar', async () => {
    await render();
    dialog.open.mockReturnValue({ afterClosed: () => of(doc('d-new', 'new-page')) });

    await clickMenuItem('n-overview', 'content-book-add-child');
    expect((bookService.replaceNodes.mock.calls[0][1] as ContentBookNode[]).find((node) => node.documentId === 'd-new')?.parentId).toBe('n-overview');

    byTestId('content-book-add-document')?.click();
    fixture.detectChanges();
    (globalThis.document.querySelector('[data-testid="content-book-add-after"]') as HTMLButtonElement).click();
    await flush();
    expect(dialog.open).toHaveBeenCalledTimes(2);
  });

  it('deletes a document from the node menu', async () => {
    await render();
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });

    await clickMenuItem('n-notes', 'content-book-delete-document');

    expect(documentService.delete).toHaveBeenCalledWith('d-notes');
  });

  it('does nothing without a loaded book', async () => {
    bookService.findBySlug.mockResolvedValue(undefined);
    await render();

    await component().addDocument('root');
    await component().moveNode('n-overview', 'down');
    await component().deleteDocument('n-overview');

    expect(dialog.open).not.toHaveBeenCalled();
    expect(bookService.replaceNodes).not.toHaveBeenCalled();
  });

  it('does not save a book that has no id yet', async () => {
    await render();
    component().book.set({ ...book(), id: undefined } as unknown as ContentBook);

    await component().moveNode('n-overview', 'down');

    expect(bookService.replaceNodes).not.toHaveBeenCalled();
  });

  it('files nothing when the dialog returns a document without an id', async () => {
    await render();
    dialog.open.mockReturnValue({ afterClosed: () => of(new Document(undefined, 'org', 'x', 'X')) });

    await component().addDocument('root');

    expect(bookService.replaceNodes).not.toHaveBeenCalled();
  });

  it('does not select a page whose save failed', async () => {
    await render();
    dialog.open.mockReturnValue({ afterClosed: () => of(doc('d-new', 'new-page')) });
    bookService.replaceNodes.mockRejectedValue(new Error('nope'));

    await component().addDocument('root');

    expect(component().actionError()).toBeDefined();
    expect(component().selectedNodeId()).toBe('n-overview');
  });

  it('explains a refused save from the server’s error text, and from a bare string', async () => {
    await render();
    const translate = vi.spyOn(TestBed.inject(TranslocoService), 'translate');
    const sentMessages = (): string[] => translate.mock.calls.filter(([key]) => String(key).endsWith('save_error')).map(([, params]) => (params as { message: string }).message);
    bookService.replaceNodes.mockRejectedValueOnce(new HttpErrorResponse({ status: 403, error: { errorText: 'Forbidden here' } }));
    await component().moveNode('n-overview', 'down');
    bookService.replaceNodes.mockRejectedValueOnce(new HttpErrorResponse({ status: 500 }));
    await component().moveNode('n-overview', 'down');
    bookService.replaceNodes.mockRejectedValueOnce('plain');
    await component().moveNode('n-overview', 'down');

    expect(sentMessages()).toEqual(['Forbidden here', expect.stringContaining('500'), 'plain']);
  });

  it('keeps the tree and says so when deleting a document fails', async () => {
    await render();
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    const translate = vi.spyOn(TestBed.inject(TranslocoService), 'translate');
    documentService.delete.mockReturnValue(throwError(() => new Error('in use')));

    await component().deleteDocument('n-notes');

    expect(translate).toHaveBeenCalledWith(expect.stringContaining('save_error'), { message: 'in use' });
    expect(component().book()?.nodes).toEqual(NODES);
  });

  it('keeps the open page when another document is deleted, and ignores an unknown node', async () => {
    await render();
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });

    await component().deleteDocument('n-notes');
    expect(component().selectedNodeId()).toBe('n-overview');

    dialog.open.mockClear();
    await component().deleteDocument('n-missing');
    expect(dialog.open).not.toHaveBeenCalled();
  });

  it('names a deleted document by id when the reader never saw its title, and clears the selection of an emptied book', async () => {
    documentService.findByQuery.mockReturnValue(of({ content: [], number: 0, size: 1, totalElements: 0, totalPages: 0 }));
    bookService.findBySlug.mockResolvedValue(book([NODES[0]]));
    await render();
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });

    await component().deleteDocument('n-overview');

    expect(dialog.open.mock.calls[0][1].data).toEqual({ title: 'd-overview' });
    expect(component().selectedNodeId()).toBeUndefined();
  });
  describe('toolbar', () => {
    const translated = (document: Document, status: DocumentStatus, revision = 2, publishedRevision: number | null = 1): Document => {
      document.translations = [{ locale: 'en', status, revision, publishedRevision }];
      return document;
    };
    const archivedOne = (document: Document): Document => {
      document.archive = { archivedAt: '2026-10-19T10:00:00Z', bucket: 'b', objectId: 'o', name: 'n', mimeType: 'application/json' };
      return document;
    };
    const listing = (...content: Document[]) => of({ content, number: 0, size: content.length, totalElements: content.length, totalPages: 1 });

    it('shows the page’s publication state and revisions', async () => {
      documentService.findByQuery.mockReturnValue(listing(translated(doc('d-overview', 'overview'), DocumentStatus.PUBLISHED_WITH_DRAFT_CHANGES, 4, 3)));
      await render({ locale: 'en' });

      expect(component().pageState()).toEqual({ kind: 'changed', label: expect.any(String) });
      expect(byTestId('content-book-status')).not.toBeNull();
      expect(component().canPublishPage()).toBe(true);
    });

    it('tells a draft, a published and an archived page apart', async () => {
      documentService.findByQuery.mockReturnValue(
        listing(
          translated(doc('d-overview', 'overview'), DocumentStatus.DRAFT),
          translated(doc('d-details', 'details'), DocumentStatus.PUBLISHED),
          archivedOne(translated(doc('d-notes', 'notes'), DocumentStatus.PUBLISHED)),
        ),
      );
      await render({ locale: 'en' });

      expect(component().pageState()?.kind).toBe('draft');
      component().select('n-details');
      expect(component().pageState()?.kind).toBe('published');
      expect(component().canPublishPage()).toBe(false);
      component().select('n-notes');
      expect(component().pageState()?.kind).toBe('archived');
      expect(component().canArchivePage()).toBe(false);
      expect(component().treeNodes().find((node) => node.id === 'n-notes')?.icon).toBe('inventory_2');
    });

    it('shows no state for a page without a translation, and falls back to the source language', async () => {
      await render({ locale: 'en' });
      expect(component().pageState()).toBeUndefined();

      documentService.findByQuery.mockReturnValue(listing(translated(doc('d-overview', 'overview'), DocumentStatus.DRAFT)));
      await render({ locale: 'de' });
      expect(component().pageState()?.kind).toBe('draft');
    });

    it('publishes the translation the page shows, and reloads its state', async () => {
      documentService.findByQuery.mockReturnValue(listing(translated(doc('d-overview', 'overview'), DocumentStatus.DRAFT)));
      await render({ locale: 'en' });
      documentService.findByQuery.mockReturnValue(listing(translated(doc('d-overview', 'overview'), DocumentStatus.PUBLISHED, 2, 2)));

      await component().publishPage();

      expect(documentService.publishTranslation).toHaveBeenCalledWith('d-overview', 'en');
      expect(component().pageState()?.kind).toBe('published');
    });

    it('publishes nothing for a page without a translation, and says so when publishing fails', async () => {
      await render({ locale: 'en' });
      await component().publishPage();
      expect(documentService.publishTranslation).not.toHaveBeenCalled();

      documentService.findByQuery.mockReturnValue(listing(translated(doc('d-overview', 'overview'), DocumentStatus.DRAFT)));
      await render({ locale: 'en' });
      documentService.publishTranslation.mockRejectedValue(new Error('not publishable'));
      await component().publishPage();
      expect(component().actionError()).toBeDefined();
    });

    it('archives the page after asking, and not when the reader thinks better of it', async () => {
      documentService.findByQuery.mockReturnValue(listing(translated(doc('d-overview', 'overview'), DocumentStatus.PUBLISHED)));
      await render({ locale: 'en' });
      dialog.open.mockReturnValue({ afterClosed: () => of(false) });
      await component().archivePage();
      expect(documentService.archive).not.toHaveBeenCalled();

      documentService.archive.mockResolvedValue(archivedOne(translated(doc('d-overview', 'overview'), DocumentStatus.PUBLISHED)));
      dialog.open.mockReturnValue({ afterClosed: () => of(true) });
      await component().archivePage();

      expect(documentService.archive).toHaveBeenCalledWith('d-overview');
      expect(component().pageState()?.kind).toBe('archived');
    });

    it('folds unpublished and archived pages away when switched off, and always for readers', async () => {
      documentService.findByQuery.mockReturnValue(
        listing(
          translated(doc('d-overview', 'overview'), DocumentStatus.DRAFT),
          translated(doc('d-details', 'details'), DocumentStatus.PUBLISHED),
          translated(doc('d-notes', 'notes'), DocumentStatus.PUBLISHED),
        ),
      );
      await render({ locale: 'en' });
      expect(component().treeNodes().map((node) => node.id)).toEqual(['n-overview', 'n-notes']);

      component().showUnpublished.set(false);
      // The draft overview folds away, and its published child moves up into its place.
      expect(component().treeNodes().map((node) => node.id)).toEqual(['n-details', 'n-notes']);

      component().showUnpublished.set(true);
      roles.set(['reader']);
      expect(component().treeNodes().map((node) => node.id)).toEqual(['n-details', 'n-notes']);
      fixture.detectChanges();
      expect(byTestId('content-book-show-unpublished')).toBeNull();
    });

    it('offers no Properties where the application mounts no Documents screens', async () => {
      await render();
      expect(component().propertiesUrl()).toBeUndefined();
      expect(() => component().openProperties()).not.toThrow();
    });
  });

  it('finds the Documents screens on the nearest ancestor route that mounts them', () => {
    const route = (path: string, children: { data?: Record<string, unknown> }[] | undefined, parent: ActivatedRoute | null) =>
      ({ routeConfig: children ? { children } : null, parent, snapshot: { url: path ? [{ path }] : [] } }) as unknown as ActivatedRoute;
    const root = route('', undefined, null);
    const samples = route('samples', [{ data: { entityName: 'Document' } }], root);
    const handbook = route('handbook', [], samples);
    Object.assign(root, { pathFromRoot: [root] });
    Object.assign(samples, { pathFromRoot: [root, samples] });
    Object.assign(handbook, { pathFromRoot: [root, samples, handbook] });

    expect(documentScreensUrl(handbook)).toBe('/samples');
    expect(documentScreensUrl(root)).toBeUndefined();
    expect(documentScreensUrl(null)).toBeUndefined();
  });
});
