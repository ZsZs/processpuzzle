import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ContentBook } from '../domain/content-book';
import { ContentBookMapper } from '../domain/content-book.mapper';
import { ContentBookService } from '../domain/content-book.service';
import { ContentBookStore } from '../domain/content-book.store';
import { ContentBookFacade } from './content-book.facade';

describe('ContentBookFacade', () => {
  let facade: ContentBookFacade;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: 'http://localhost:3000/organizations/processpuzzle-testbed' } } },
        ContentBookFacade,
      ],
    });
    facade = TestBed.inject(ContentBookFacade);
  });

  it('registers for ContentBook', () => {
    expect(facade.entityType).toBe(ContentBook);
  });

  it('reuses the root-provided mapper, service and store', () => {
    expect(facade.mapper).toBe(TestBed.inject(ContentBookMapper));
    expect(facade.service).toBe(TestBed.inject(ContentBookService));
    expect(facade.storeClass).toBe(ContentBookStore);
    expect(facade.store).toBe(TestBed.inject(ContentBookStore));
  });

  it('binds the store to the descriptor it hands out', () => {
    expect(facade.descriptor.store).toBe(facade.store);
    expect(facade.attrDescriptors.length).toBeGreaterThan(0);
  });
});
