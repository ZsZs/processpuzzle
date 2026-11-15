import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { EmbeddedCollectionService, itemVersionOf, mergePatch } from './embedded-collection.service';

describe('EmbeddedCollectionService', () => {
  let http: HttpTestingController;
  let service: EmbeddedCollectionService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { ENTITY_SERVICE_ROOT: 'https://api/organizations/acme' } } }],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(EmbeddedCollectionService);
  });

  it('lists the items, with the object version the server sent as ETag', async () => {
    const result = service.list('Race', 'r-1', 'rounds');
    const request = http.expectOne('https://api/organizations/acme/entities/Race/r-1/rounds');
    expect(request.request.method).toBe('GET');
    request.flush([{ id: 'a' }], { headers: { ETag: '"5"' } });

    expect(await result).toEqual({ items: [{ id: 'a' }], version: '"5"' });
  });

  it('appends one item, and answers it as stored', async () => {
    const result = service.append('Race', 'r-1', 'rounds', { number: 2 });
    const request = http.expectOne('https://api/organizations/acme/entities/Race/r-1/rounds');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ number: 2 });
    request.flush({ id: 'new', number: 2 });

    expect(await result).toEqual({ id: 'new', number: 2 });
  });

  it('patches one item, naming the version only when asked to', async () => {
    const unversioned = service.patch('Race', 'r-1', 'rounds', 'a', { number: 3 });
    const first = http.expectOne('https://api/organizations/acme/entities/Race/r-1/rounds/a');
    expect(first.request.method).toBe('PATCH');
    expect(first.request.headers.has('If-Match')).toBe(false);
    first.flush({ id: 'a', number: 3 });
    await unversioned;

    const versioned = service.patch('Race', 'r-1', 'rounds', 'a', { number: 4 }, '"5"');
    const second = http.expectOne('https://api/organizations/acme/entities/Race/r-1/rounds/a');
    expect(second.request.headers.get('If-Match')).toBe('"5"');
    second.flush({ id: 'a', number: 4 });
    await versioned;
  });

  it('removes one item', async () => {
    const result = service.remove('Race', 'r-1', 'rounds', 'a');
    const request = http.expectOne('https://api/organizations/acme/entities/Race/r-1/rounds/a');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });

    await expect(result).resolves.toBeUndefined();
  });

  it('encodes what goes into the path', async () => {
    const result = service.remove('Race Day', 'r-1', 'rounds', 'a/b');
    http.expectOne('https://api/organizations/acme/entities/Race%20Day/r-1/rounds/a%2Fb').flush(null, { status: 204, statusText: 'No Content' });

    await expect(result).resolves.toBeUndefined();
  });
});

describe('mergePatch', () => {
  it('holds the members that changed, and null for those that went away', () => {
    expect(mergePatch({ id: 'a', number: 1, label: 'x', note: 'n' }, { id: 'a', number: 2, label: 'x' })).toEqual({ number: 2, note: null });
  });

  it('compares nested values by content', () => {
    expect(mergePatch({ id: 'a', tags: ['x'] }, { id: 'a', tags: ['x'] })).toEqual({});
    expect(mergePatch({ id: 'a', tags: ['x'] }, { id: 'a', tags: ['x', 'y'] })).toEqual({ tags: ['x', 'y'] });
  });

  it('never carries the id, nor the item version the server counts', () => {
    expect(mergePatch({ id: 'a', itemVersion: 2, number: 1 }, { id: 'b', itemVersion: 9, number: 2 })).toEqual({ number: 2 });
  });
});

describe('itemVersionOf', () => {
  it('reads the item version as an If-Match value, and names none for an item stored before versions', () => {
    expect(itemVersionOf({ id: 'a', itemVersion: 3 })).toBe('3');
    expect(itemVersionOf({ id: 'a' })).toBeUndefined();
  });
});

describe('EmbeddedCollectionService — nested collections', () => {
  let http: HttpTestingController;
  let service: EmbeddedCollectionService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { ENTITY_SERVICE_ROOT: 'https://api/organizations/acme' } } }],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(EmbeddedCollectionService);
  });

  it('lists the collection nested in an item, under the item of the outer collection', async () => {
    const result = service.listNested('Race', 'r-1', 'rounds', 'round-1', 'results');
    const request = http.expectOne('https://api/organizations/acme/entities/Race/r-1/rounds/round-1/results');
    request.flush([{ id: 'x' }], { headers: { ETag: '"5"' } });

    expect(await result).toEqual({ items: [{ id: 'x' }], version: '"5"' });
  });

  it('appends, patches and removes a nested item, naming its own version on a change', async () => {
    const appended = service.appendNested('Race', 'r-1', 'rounds', 'round-1', 'results', { score: 1 });
    http.expectOne('https://api/organizations/acme/entities/Race/r-1/rounds/round-1/results').flush({ id: 'x', score: 1 });
    await appended;

    const patched = service.patchNested('Race', 'r-1', 'rounds', 'round-1', 'results', 'x', { score: 2 }, '2');
    const patch = http.expectOne('https://api/organizations/acme/entities/Race/r-1/rounds/round-1/results/x');
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.headers.get('If-Match')).toBe('2');
    patch.flush({ id: 'x', score: 2 });
    await patched;

    const removed = service.removeNested('Race', 'r-1', 'rounds', 'round-1', 'results', 'x');
    http.expectOne('https://api/organizations/acme/entities/Race/r-1/rounds/round-1/results/x').flush(null, { status: 204, statusText: 'No Content' });
    await expect(removed).resolves.toBeUndefined();
  });
});
