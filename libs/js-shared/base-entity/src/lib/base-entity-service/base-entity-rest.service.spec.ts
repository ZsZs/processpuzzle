import { TestBed } from '@angular/core/testing';
import { TestEntityService } from './test-entity.service';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestEntityMapper } from '../test-entity.mapper';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { TestEntity, TestEnum } from '../test-entity';
import { BaseEntityLoadResponse, BaseEntityQueryCondition, FilterCondition } from './base-entity-load-response';
import { TestConfiguration } from '../test-configuration';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';

describe('BaseEntityService', () => {
  const pathParams = new Map<string, string>([['messageId', '123']]);
  const filters: FilterCondition[] = [{ property: 'xpath', operator: '==', value: '/ie4q01' }];
  const queryCondition: BaseEntityQueryCondition = { pathParams, filters };
  const expectedEntity = new TestEntity('1', 'hello', 'anything', false, 100, new Date('2024-01-18T20:02:27.000Z'), TestEnum.VALUE_FOUR);
  const expectedUrl = 'http://localhost:4200/services/generic-message/api/v1/message/123/node?xpath=%2Fie4q01';
  const expectedPagedUrl = 'http://localhost:4200/services/generic-message/api/v1/message/123/node?page=1&xpath=%2Fie4q01';
  const payload = { id: '1', name: 'hello', description: 'anything', boolean: false, number: 100, date: '2024-01-18T20:02:27.000Z', enumValue: 3 };
  let baseEntityService: TestEntityService;
  let controller: HttpTestingController;
  let runtimeConfigMock!: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: string } };

  beforeEach(() => {
    runtimeConfigMock = { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: 'http://localhost:4200/services/generic-message/api/v1' } };

    TestBed.configureTestingModule({
      imports: [],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: runtimeConfigMock },
        { provide: TestConfiguration, useValue: runtimeConfigMock },
        TestEntityMapper,
        TestEntityService,
      ],
    });
    baseEntityService = TestBed.inject(TestEntityService);
    controller = TestBed.inject(HttpTestingController);
  });

  it('findByQuery() calculates resource URL from Environment, path and query parameters', () => {
    baseEntityService.findByQuery(queryCondition).subscribe(() => {
      // do nothing here
    });
    controller.expectOne(expectedUrl);
    controller.verify();
  });

  it('findByQuery() calculates resource URL from Environment, path, query parameters and page', () => {
    baseEntityService.findByQuery({ ...queryCondition, ...{ page: 1 } }).subscribe(() => {
      // do nothing here
    });
    controller.expectOne(expectedPagedUrl);
    controller.verify();
  });

  it('findByQuery() maps response body to array of entities if no page specified.', () => {
    let actualEntities: Array<TestEntity> | undefined;
    baseEntityService.findByQuery(queryCondition).subscribe((entities) => {
      actualEntities = entities as TestEntity[];
      expect(actualEntities).toEqual([expectedEntity]);
    });
    const request = controller.expectOne(expectedUrl);
    request.flush([payload]);
    controller.verify();
    expect(actualEntities).toEqual([expectedEntity]);
  });

  it('findByQuery() maps response body to array of entities if no page specified.', () => {
    let actualResponse: BaseEntityLoadResponse<TestEntity> | undefined;
    baseEntityService.findByQuery({ ...queryCondition, ...{ page: 1 } }).subscribe((response) => {
      actualResponse = response as BaseEntityLoadResponse<TestEntity>;
    });
    const request = controller.expectOne(expectedPagedUrl);
    request.flush([{ page: 1, pageSize: 10, totalPageCount: 8, content: [payload] }]);
    controller.verify();
    expect(actualResponse).toEqual({ page: 1, pageSize: 10, totalPageCount: 8, content: [expectedEntity] });
  });

  it('findByQuery() fails when HTTP Error occurs.', () => {
    const status = 500;
    const statusText = 'Internal Server Error';
    const errorEvent = new ErrorEvent('API error');
    let actualError: HttpErrorResponse | undefined;

    baseEntityService.findByQuery(queryCondition).subscribe(
      () => expect.fail('next handler must be called'),
      (error) => (actualError = error),
      () => expect.fail('complete handler must be called'),
    );
    const request = controller.expectOne(expectedUrl);
    request.error(errorEvent, { status, statusText });
    expect(actualError?.error).toBe(errorEvent);
    expect(actualError?.status).toBe(status);
    expect(actualError?.statusText).toBe(statusText);
  });

  describe('findAll()', () => {
    it('delegates to findByQuery with the supplied page', () => {
      baseEntityService.findAll(2).subscribe();
      const request = controller.expectOne((req) => req.url.includes('/node') && req.url.includes('page=2'));
      expect(request.request.method).toBe('GET');
      request.flush([{ page: 2, pageSize: 10, totalPageCount: 4, content: [payload] }]);
      controller.verify();
    });

    it('omits the page query param when no page is supplied', () => {
      baseEntityService.findAll().subscribe();
      const request = controller.expectOne((req) => req.url.includes('/node') && !req.url.includes('page='));
      expect(request.request.method).toBe('GET');
      request.flush([payload]);
      controller.verify();
    });

    it('wraps a single-object simple response into an array', () => {
      let actual: TestEntity[] | undefined;
      baseEntityService.findAll().subscribe((entities) => (actual = entities as TestEntity[]));
      const request = controller.expectOne((req) => req.url.includes('/node'));
      request.flush(payload);
      controller.verify();
      expect(actual).toEqual([expectedEntity]);
    });
  });

  describe('findById()', () => {
    it('builds an id-scoped URL and maps the single-object response', () => {
      let actual: TestEntity | undefined;
      baseEntityService.findById('1').subscribe((entity) => (actual = entity as TestEntity));
      const request = controller.expectOne((req) => req.url.endsWith('/node') && !req.params.keys().length);
      expect(request.request.method).toBe('GET');
      request.flush(payload);
      controller.verify();
      expect(actual).toEqual(expectedEntity);
    });
  });

  describe('add()', () => {
    it('POSTs the mapped DTO and returns the persisted entity', () => {
      let actual: TestEntity | undefined;
      baseEntityService.add(expectedEntity).subscribe((entity) => (actual = entity as TestEntity));
      const request = controller.expectOne((req) => req.method === 'POST' && req.url.includes('/node'));
      expect(request.request.body).toEqual(expectedEntity);
      request.flush(payload);
      controller.verify();
      expect(actual).toEqual(expectedEntity);
    });
  });

  describe('update()', () => {
    it('PUTs the mapped DTO to the id-scoped resource URL', () => {
      let actual: TestEntity | undefined;
      baseEntityService.update(expectedEntity).subscribe((entity) => (actual = entity as TestEntity));
      const request = controller.expectOne((req) => req.method === 'PUT' && req.url.includes(`/node/${expectedEntity.id}`));
      expect(request.request.body).toEqual(expectedEntity);
      request.flush(payload);
      controller.verify();
      expect(actual).toEqual(expectedEntity);
    });
  });

  describe('delete()', () => {
    it('DELETEs the id-scoped resource URL', () => {
      baseEntityService.delete('abc').subscribe();
      const request = controller.expectOne((req) => req.method === 'DELETE' && req.url.includes('/node/abc'));
      expect(request.request.method).toBe('DELETE');
      request.flush(null);
      controller.verify();
    });
  });

  describe('deleteAll()', () => {
    it('DELETEs the unscoped resource URL (resourceUrl as-is, without baseUrl)', () => {
      baseEntityService.deleteAll().subscribe();
      const request = controller.expectOne((req) => req.method === 'DELETE' && req.url === 'message/%{messageId}/node');
      expect(request.request.url).toBe('message/%{messageId}/node');
      request.flush(null);
      controller.verify();
    });
  });

  describe('buildFullUrl() branches', () => {
    it('returns a URL with no query string when there are neither page nor filters', () => {
      baseEntityService.findByQuery({ pathParams }).subscribe();
      const request = controller.expectOne((req) => req.url.endsWith('message/123/node'));
      expect(request.request.params.keys().length).toBe(0);
      request.flush([payload]);
      controller.verify();
    });

    it('appends the hash fragment when supplied', () => {
      baseEntityService.findByQuery({ pathParams, hash: 'section-1' }).subscribe();
      const request = controller.expectOne((req) => req.urlWithParams.includes('#section-1'));
      expect(request.request.urlWithParams).toContain('#section-1');
      request.flush([payload]);
      controller.verify();
    });
  });
});
