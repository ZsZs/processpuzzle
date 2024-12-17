import { TestBed } from '@angular/core/testing';
import { TestEntityService } from './test-entity.service';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestEntityMapper } from './test-entity.mapper';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { TestEntity, TestEnum } from './test-entity';
import { BaseEntityLoadResponse } from './base-entity-load-response';
import { TestConfiguration } from './test-configuration';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';

describe('BaseEntityService', () => {
  const pathParams = new Map<string, string>([['messageId', '123']]);
  const queryParams = new Map<string, string>([['xpath', '/ie4q01']]);
  const expectedEntity = new TestEntity('1', 'hello', 'anything', false, 100, new Date('2024-01-18T20:02:27.000Z'), TestEnum.VALUE_FOUR);
  const expectedUrl = 'http://localhost:4200/services/generic-message/api/v1/message/123/node?xpath=%2Fie4q01';
  const expectedPagedUrl = 'http://localhost:4200/services/generic-message/api/v1/message/123/node?page=1&xpath=%2Fie4q01';
  const payload = { id: '1', name: 'hello', description: 'anything', boolean: false, number: 100, date: '2024-01-18T20:02:27.000Z', enumValue: 3 };
  let baseEntityService: TestEntityService;
  let controller: HttpTestingController;
  let runtimeConfigMock!: { TEST_SERVICE_ROOT: string };

  beforeEach(() => {
    runtimeConfigMock = { TEST_SERVICE_ROOT: 'http://localhost:4200/services/generic-message/api/v1' };

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

  it('findAll() calculates resource URL from Environment, path and query parameters', () => {
    baseEntityService.findAll(pathParams, queryParams).subscribe(() => {
      // do nothing here
    });
    controller.expectOne(expectedUrl);
    controller.verify();
  });

  it('findAll() calculates resource URL from Environment, path, query parameters and page', () => {
    baseEntityService.findAll(pathParams, queryParams, 1).subscribe(() => {
      // do nothing here
    });
    controller.expectOne(expectedPagedUrl);
    controller.verify();
  });

  it('findAll() maps response body to array of entities if no page specified.', () => {
    let actualEntities: Array<TestEntity> | undefined;
    baseEntityService.findAll(pathParams, queryParams).subscribe((entities) => {
      actualEntities = entities as TestEntity[];
      expect(actualEntities).toEqual([expectedEntity]);
    });
    const request = controller.expectOne(expectedUrl);
    request.flush([payload]);
    controller.verify();
    expect(actualEntities).toEqual([expectedEntity]);
  });

  it('findAll() maps response body to array of entities if no page specified.', () => {
    let actualResponse: BaseEntityLoadResponse<TestEntity> | undefined;
    baseEntityService.findAll(pathParams, queryParams, 1).subscribe((response) => {
      actualResponse = response as BaseEntityLoadResponse<TestEntity>;
    });
    const request = controller.expectOne(expectedPagedUrl);
    request.flush([{ page: 1, pageSize: 10, totalPageCount: 8, content: [payload] }]);
    controller.verify();
    expect(actualResponse).toEqual({ page: 1, pageSize: 10, totalPageCount: 8, content: [expectedEntity] });
  });

  it('findAll() fails when HTTP Error occurs.', () => {
    const status = 500;
    const statusText = 'Internal Server Error';
    const errorEvent = new ErrorEvent('API error');
    let actualError: HttpErrorResponse | undefined;

    baseEntityService.findAll(pathParams, queryParams).subscribe(
      () => fail('next handler must be called'),
      (error) => (actualError = error),
      () => fail('complete handler must be called'),
    );
    const request = controller.expectOne(expectedUrl);
    request.error(errorEvent, { status, statusText });
    expect(actualError?.error).toBe(errorEvent);
    expect(actualError?.status).toBe(status);
    expect(actualError?.statusText).toBe(statusText);
  });
});
