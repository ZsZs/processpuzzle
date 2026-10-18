import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { CalculationDefinitionStore } from './calculation-definition.store';
import { ORDER_TOTAL_DTO, SERIES_RANKING_DTO, SERVICE_ROOT } from './test-calculation-definition';

describe('CalculationDefinitionStore', () => {
  let store: InstanceType<typeof CalculationDefinitionStore>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { CALCULATION_SERVICE_ROOT: SERVICE_ROOT } } }],
    });
    store = TestBed.inject(CalculationDefinitionStore);
    TestBed.inject(HttpTestingController).expectOne(`${SERVICE_ROOT}/calculation-definitions`).flush([ORDER_TOTAL_DTO, SERIES_RANKING_DTO]);
  });

  it('lists the calculations on init and selects one by id', () => {
    expect(store.entities().map((definition) => definition.id)).toEqual(['order-total', 'sail-series-ranking']);

    store.setCurrentEntity('sail-series-ranking');

    expect(store.currentEntity()?.source).toContain('sail-series-ranking');
  });
});
