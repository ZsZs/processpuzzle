import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { CalculationApiService } from './calculation-api.service';
import { CALCULATION_RUN, SERVICE_ROOT, TRACE } from './test-calculation-definition';

describe('CalculationApiService', () => {
  let service: CalculationApiService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { CALCULATION_SERVICE_ROOT: SERVICE_ROOT } } }],
    });
    service = TestBed.inject(CalculationApiService);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('validates a source', async () => {
    const pending = firstValueFrom(service.validate('calculation: x'));
    const request = controller.expectOne(`${SERVICE_ROOT}/calculation-definitions/validate`);
    expect(request.request.body).toEqual({ source: 'calculation: x' });
    request.flush({ valid: false, errors: ['bad'] });

    expect((await pending).errors).toEqual(['bad']);
  });

  it('evaluates the draft, or an unsaved source, and runs the released definition', async () => {
    service.evaluate('order total', { order: 'o-1' }).subscribe();
    const evaluation = controller.expectOne(`${SERVICE_ROOT}/calculations/order%20total/evaluate`);
    expect(evaluation.request.body).toEqual({ parameters: { order: 'o-1' }, revision: 'draft', source: undefined });
    evaluation.flush({});

    service.evaluate('order-total', {}, 'released', 'calculation: order-total').subscribe();
    expect(controller.expectOne(`${SERVICE_ROOT}/calculations/order-total/evaluate`).request.body.source).toBe('calculation: order-total');

    const run = firstValueFrom(service.run('order-total', { order: 'o-1' }));
    const runRequest = controller.expectOne(`${SERVICE_ROOT}/calculations/order-total/runs`);
    expect(runRequest.request.body.revision).toBe('released');
    runRequest.flush(CALCULATION_RUN);
    expect((await run).status).toBe('COMPLETED');
  });

  it('lists, reads, traces and freezes runs', () => {
    service.runs('order-total', 'o-1').subscribe();
    const list = controller.expectOne((request) => request.url === `${SERVICE_ROOT}/calculation-runs`);
    expect(list.request.params.get('calculationId')).toBe('order-total');
    expect(list.request.params.get('subjectId')).toBe('o-1');
    list.flush([]);

    service.runs().subscribe();
    expect(controller.expectOne(`${SERVICE_ROOT}/calculation-runs`).request.params.keys()).toEqual([]);

    service.findRun('run-1').subscribe();
    controller.expectOne(`${SERVICE_ROOT}/calculation-runs/run-1`).flush(CALCULATION_RUN);
    service.trace('run-1').subscribe();
    controller.expectOne(`${SERVICE_ROOT}/calculation-runs/run-1/trace`).flush(TRACE);
    service.freeze('run-1').subscribe();
    const freeze = controller.expectOne(`${SERVICE_ROOT}/calculation-runs/run-1/freeze`);
    expect(freeze.request.method).toBe('POST');
    freeze.flush(CALCULATION_RUN);
  });
});
