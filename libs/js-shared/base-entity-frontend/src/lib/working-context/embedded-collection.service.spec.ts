import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { EmbeddedCollectionService, mergePatch } from './embedded-collection.service';

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

  it('never carries the id', () => {
    expect(mergePatch({ id: 'a' }, { id: 'b' })).toEqual({});
  });
});
