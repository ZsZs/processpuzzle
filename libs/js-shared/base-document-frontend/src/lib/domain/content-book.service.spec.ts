import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { ContentBook } from './content-book';
import { ContentBookMapper } from './content-book.mapper';
import { contentOf, ContentBookService } from './content-book.service';
import { mayEdit } from './document-access-context';

describe('ContentBookService', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  const dto = {
    id: 'b-1',
    orgKey: 'processpuzzle-testbed',
    slug: 'handbook',
    title: 'Handbook',
    isPublic: true,
    readerRoles: ['reader'],
    editorRoles: ['editor'],
    nodes: [{ id: 'n-1', documentId: 'd-1', orderIndex: 0 }],
    lockVersion: 3,
  };
  let service: ContentBookService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: serviceRoot } } }],
    });
    service = TestBed.inject(ContentBookService);
    controller = TestBed.inject(HttpTestingController);
  });

  it('finds a book by its slug', async () => {
    const found = service.findBySlug('handbook');

    const request = controller.expectOne((req) => req.url.startsWith(`${serviceRoot}/content-books?`));
    expect(new URL(request.request.url).searchParams.get('where')).toBe('slug==handbook');
    request.flush({ content: [dto], totalElements: 1, totalPages: 1, number: 0, size: 1 });

    const book = await found;
    expect(book?.title).toBe('Handbook');
    expect(book?.version).toBe(3);
    expect(book?.nodes).toEqual([{ id: 'n-1', documentId: 'd-1', parentId: null, orderIndex: 0 }]);
  });

  it('answers undefined for a slug no readable book has', async () => {
    const found = service.findBySlug('missing');

    controller.expectOne((req) => req.url.startsWith(`${serviceRoot}/content-books?`)).flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 1 });

    expect(await found).toBeUndefined();
  });

  it('replaces the whole tree in one write', async () => {
    const replaced = service.replaceNodes('b-1', [{ id: 'n-2', documentId: 'd-2', orderIndex: 4 }]);

    const request = controller.expectOne(`${serviceRoot}/content-books/b-1/nodes`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ nodes: [{ id: 'n-2', documentId: 'd-2', parentId: null, orderIndex: 4 }] });
    request.flush({ ...dto, nodes: [{ id: 'n-2', documentId: 'd-2', parentId: null, orderIndex: 0 }] });

    expect((await replaced).nodes[0].id).toBe('n-2');
  });

  it('saves the properties without the tree, so a form save cannot revert a reorder', async () => {
    const book = new ContentBook('b-1', 'org', 'handbook', 'Handbook', 'All of it', false, [], ['editor'], [{ id: 'n', documentId: 'd', orderIndex: 0 }], 3);
    const saved = firstValueFrom(service.update({ ...book, id: 'b-1' }));

    const request = controller.expectOne(`${serviceRoot}/content-books/b-1`);
    expect(request.request.body).toEqual({ slug: 'handbook', title: 'Handbook', description: 'All of it', isPublic: false, readerRoles: [], editorRoles: ['editor'] });
    request.flush(dto);

    expect((await saved).slug).toBe('handbook');
  });
});

describe('ContentBookMapper', () => {
  it('fills the defaults an older or sparse payload leaves out', () => {
    const book = new ContentBookMapper().fromDto({ id: 'b', slug: 'x', title: 'X', version: 2 });

    expect(book.isPublic).toBe(false);
    expect(book.readerRoles).toEqual([]);
    expect(book.editorRoles).toEqual([]);
    expect(book.nodes).toEqual([]);
    expect(book.version).toBe(2);
  });

  it('keeps what a full payload carries', () => {
    const book = new ContentBookMapper().fromDto({
      id: 'b',
      slug: 'x',
      title: 'X',
      description: 'Why',
      isPublic: true,
      nodes: [{ id: 'n', documentId: 'd', parentId: 'p', orderIndex: 3 }],
      lockVersion: 5,
      version: 2,
    });

    expect(book.description).toBe('Why');
    expect(book.isPublic).toBe(true);
    expect(book.nodes).toEqual([{ id: 'n', documentId: 'd', parentId: 'p', orderIndex: 3 }]);
    expect(book.version).toBe(5);
  });
});

describe('contentOf', () => {
  it('reads all three shapes a query answers with', () => {
    const book = new ContentBook('b');

    expect(contentOf([book])).toEqual([book]);
    expect(contentOf({ content: [book], number: 0, size: 1, totalElements: 1, totalPages: 1 })).toEqual([book]);
    expect(contentOf(book)).toEqual([book]);
  });
});

describe('mayEdit', () => {
  it('offers editing when the application wires no access context', () => {
    expect(mayEdit(null, ['editor'])).toBe(true);
  });

  it('follows the backend rule: an empty list means any signed-in member', () => {
    expect(mayEdit({ roles: signal([]) }, [])).toBe(true);
    expect(mayEdit({ roles: signal([]), authenticated: signal(false) }, [])).toBe(false);
  });

  it('requires one of the editor roles when there are any', () => {
    expect(mayEdit({ roles: signal(['editor']) }, ['editor', 'admin'])).toBe(true);
    expect(mayEdit({ roles: signal(['reader']) }, ['editor'])).toBe(false);
  });
});
