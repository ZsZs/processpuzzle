import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ExchangeRates, RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { NGXLogger } from 'ngx-logging-kit';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ExchangeRateRecord } from './exchange-rate.service';
import { OrganizationExchangeRates, tableOf } from './organization-exchange-rates';

const RATES_URL = 'http://localhost:8080/organizations/acme/exchange-rates?page=0&size=200';
const EUR_USD: ExchangeRateRecord = { id: 'r1', from: 'EUR', to: 'USD', rate: '1.10', validFrom: '2026-01-01', version: 1 };

describe('the organization’s exchange rates', () => {
  let store: OrganizationExchangeRates;
  let controller: HttpTestingController;

  function setup(baseConfiguration: object = { ENTITY_SERVICE_ROOT: 'http://localhost:8080/organizations/acme' }) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: NGXLogger, useValue: { warn: vi.fn() } }, { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: baseConfiguration } }],
    });
    store = TestBed.inject(OrganizationExchangeRates);
    controller = TestBed.inject(HttpTestingController);
  }

  beforeEach(() => setup());

  it('has no rates until they are loaded, so money converts to nothing but itself', () => {
    expect(store.rates()).toBe(ExchangeRates.NONE);
  });

  it('loads the rates once, however many callers wait, and again after it is invalidated', async () => {
    const first = Promise.all([store.ensureLoaded(), store.ensureLoaded()]);
    controller.expectOne(RATES_URL).flush({ content: [EUR_USD] });
    await first;
    expect(store.rates().rate('EUR', 'USD', '2026-03-01').toString()).toBe('1.1');

    await store.ensureLoaded();
    controller.expectNone(RATES_URL);

    store.invalidate();
    const again = store.ensureLoaded();
    controller.expectOne(RATES_URL).flush({ content: [{ ...EUR_USD, rate: '1.25' }] });
    await again;
    expect(store.rates().rate('EUR', 'USD', '2026-03-01').toString()).toBe('1.25');
  });

  it('accepts a bare array, and is no rates where the call fails, the body is odd or the host serves none', async () => {
    const bare = store.ensureLoaded();
    controller.expectOne(RATES_URL).flush([EUR_USD]);
    await bare;
    expect(store.rates().currencies()).toEqual(['EUR', 'USD']);

    setup();
    const failed = store.ensureLoaded();
    controller.expectOne(RATES_URL).flush('nope', { status: 500, statusText: 'Server Error' });
    await failed;
    expect(store.rates()).toBe(ExchangeRates.NONE);

    setup();
    const odd = store.ensureLoaded();
    controller.expectOne(RATES_URL).flush('<html></html>');
    await odd;
    expect(store.rates()).toBe(ExchangeRates.NONE);

    setup({});
    await store.ensureLoaded();
    controller.expectNone(RATES_URL);
    expect(store.rates()).toBe(ExchangeRates.NONE);
  });
});

describe('tableOf', () => {
  it('leaves out a rate the table refuses and keeps the others, saying so', () => {
    const logger = { warn: vi.fn() };
    const table = tableOf([EUR_USD, { ...EUR_USD, rate: '0' }, { ...EUR_USD, rate: '9' }, { from: 'EUR', to: 'HUF', rate: '400', validFrom: '2026-01-01' }], logger);

    expect(table.rate('EUR', 'USD', '2026-03-01').toString()).toBe('1.1');
    expect(table.rate('EUR', 'HUF', '2026-03-01').toString()).toBe('400');
    expect(logger.warn).toHaveBeenCalledTimes(2);
  });

  it('is the empty table for no usable rates', () => {
    expect(tableOf([])).toBe(ExchangeRates.NONE);
    expect(tableOf([{ ...EUR_USD, rate: 'a lot' }])).toBe(ExchangeRates.NONE);
  });
});
