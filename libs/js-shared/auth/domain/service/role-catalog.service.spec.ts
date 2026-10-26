import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TAG_SUGGESTION_SOURCES, type TagSuggestionSource } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { provideRoleCatalogSuggestions, RoleCatalogService } from './role-catalog.service';

const ROOT = 'https://api.example.test/organizations/demo';

function setup(root?: string) {
  const configuration = root === undefined ? {} : { BASE_CONFIGURATION: { SELF_SERVICE_ROLES_SERVICE_ROOT: root } };
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: configuration }, provideRoleCatalogSuggestions()],
  });
  return { http: TestBed.inject(HttpTestingController), service: TestBed.inject(RoleCatalogService) };
}

describe('RoleCatalogService', () => {
  it('reads the catalog from the organization root', async () => {
    const { http, service } = setup(`${ROOT}/`);

    const result = firstValueFrom(service.findAll());
    http.expectOne(`${ROOT}/role-catalog`).flush([{ name: 'documentation-editor', description: 'Edits' }]);

    expect(await result).toEqual([{ name: 'documentation-editor', description: 'Edits' }]);
  });

  it('reads the catalog from its own root, without the self-service switch', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { ROLE_CATALOG_SERVICE_ROOT: ROOT } } }, provideRoleCatalogSuggestions()],
    });
    const http = TestBed.inject(HttpTestingController);

    const result = firstValueFrom(TestBed.inject(RoleCatalogService).findAll());
    http.expectOne(`${ROOT}/role-catalog`).flush([{ name: 'order-clerk' }]);

    expect(await result).toEqual([{ name: 'order-clerk' }]);
  });

  it('is empty without a configured root, and when the backend fails', async () => {
    expect(await firstValueFrom(setup().service.findAll())).toEqual([]);

    TestBed.resetTestingModule();
    const { http, service } = setup(ROOT);
    const result = firstValueFrom(service.findAll());
    http.expectOne(`${ROOT}/role-catalog`).flush('boom', { status: 503, statusText: 'Unavailable' });
    expect(await result).toEqual([]);
  });

  it('is offered to tag fields as the realm role suggestions, by name', async () => {
    const { http } = setup(ROOT);
    const [source] = TestBed.inject(TAG_SUGGESTION_SOURCES) as readonly TagSuggestionSource[];

    const result = firstValueFrom(source.suggestions());
    http.expectOne(`${ROOT}/role-catalog`).flush([{ name: 'a' }, { name: 'b' }]);

    expect(source.key).toBe('realm-roles');
    expect(await result).toEqual(['a', 'b']);
  });
});
