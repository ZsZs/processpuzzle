import { Component, input, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { LayoutService } from '@processpuzzle/util';
import { SidenavContentService } from '@processpuzzle/widgets';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Document } from '../../domain/base-document';
import { BaseDocumentService } from '../../domain/base-document.service';
import { ContentBook, ContentBookNode } from '../../domain/content-book';
import { ContentBookService } from '../../domain/content-book.service';
import { DOCUMENT_ACCESS_CONTEXT } from '../../domain/document-access-context';
import { ContentBookDocumentViewComponent } from './content-book-document-view.component';
import { ContentBookWidgetComponent } from './content-book-widget.component';

@Component({ selector: 'pp-content-book-document-view', template: '<p data-testid="page-of">{{ documentId() }}</p>' })
class PageStubComponent {
  readonly documentId = input.required<string>();
  readonly locale = input.required<string>();
  readonly document = input<Document | undefined>(undefined);
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
  let documentService: { findByQuery: ReturnType<typeof vi.fn>; delete: ReturnType<typeof vi.fn> };
  let dialog: { open: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };
  let roles: ReturnType<typeof signal<readonly string[]>>;
  let sidenavContent: SidenavContentService;
  let queryParams: Record<string, string>;

  const byTestId = (id: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
  const component = (): any => fixture.componentInstance;

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
    roles = signal<readonly string[]>(['editor']);
    bookService = { findBySlug: vi.fn().mockResolvedValue(book()), replaceNodes: vi.fn((_id: string, nodes: ContentBookNode[]) => Promise.resolve(book(nodes))) };
    documentService = {
      findByQuery: vi.fn(() => of({ content: [doc('d-overview', 'overview'), doc('d-details', 'details'), doc('d-notes', 'notes')], number: 0, size: 3, totalElements: 3, totalPages: 1 })),
      delete: vi.fn(() => of(undefined)),
    };
    dialog = { open: vi.fn() };
    router = { navigate: vi.fn().mockResolvedValue(true) };

    TestBed.configureTestingModule({
      providers: [
        provideTranslocoTesting({ translations: { en: {} } }),
        { provide: ContentBookService, useValue: bookService },
        { provide: BaseDocumentService, useValue: documentService },
        { provide: MatDialog, useValue: dialog },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useFactory: () => ({ snapshot: { queryParamMap: convertToParamMap(queryParams) } }) },
        { provide: LayoutService, useValue: { isSmallDevice: signal(false) } },
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
    expect(component().book().nodes.map((node: ContentBookNode) => node.id)).not.toContain('n-notes');
  });

  it('shows a refused save without losing the tree', async () => {
    await render();
    bookService.replaceNodes.mockRejectedValue(new Error('403'));

    await component().moveNode('n-overview', 'down');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.pp-content-book__error')).not.toBeNull();
    expect(component().book().nodes).toEqual(NODES);
  });

  it('files the document the dialog picked, and opens it', async () => {
    await render();
    const added = doc('d-new', 'new-page');
    dialog.open.mockReturnValue({ afterClosed: () => of(added) });

    await component().addDocument('n-overview');

    expect(dialog.open.mock.calls[0][1].data).toEqual(expect.objectContaining({ isPublic: true, editorRoles: ['editor'], excludeDocumentIds: ['d-overview', 'd-details', 'd-notes'] }));
    const saved = bookService.replaceNodes.mock.calls[0][1] as ContentBookNode[];
    expect(saved.find((node) => node.documentId === 'd-new')?.parentId).toBe('n-overview');
    expect(component().selectedNode()?.documentId).toBe('d-new');
  });

  it('files nothing when the dialog is cancelled', async () => {
    await render();
    dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });

    await component().addDocument(null);

    expect(bookService.replaceNodes).not.toHaveBeenCalled();
  });

  it('deletes a document after asking, and moves its pages up', async () => {
    await render();
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    component().select('n-overview');

    await component().deleteDocument('n-overview');

    expect(documentService.delete).toHaveBeenCalledWith('d-overview');
    expect(component().book().nodes.map((node: ContentBookNode) => `${node.parentId ?? '-'}/${node.id}`)).toEqual(['-/n-details', '-/n-notes']);
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
});
