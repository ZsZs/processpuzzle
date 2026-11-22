import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ExchangeRates, RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { NGXLogger } from 'ngx-logging-kit';
import { firstValueFrom } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { FlexboxDescriptor } from '../base-entity/flexboxDescriptor';
import { OrganizationExchangeRates } from '../base-form/fundamental-types/organization-exchange-rates';
import { ExchangeRateEntry } from './exchange-rate-entry';
import { ExchangeRateAuthoringService } from './exchange-rate-authoring.service';
import { createExchangeRateDescriptor } from './exchange-rate-entry.descriptors';
import { ExchangeRateEntryMapper } from './exchange-rate-entry.mapper';

const RATES_URL = 'http://localhost:8080/organizations/acme/exchange-rates';
const RELOAD_URL = `${RATES_URL}?page=0&size=200`;
const EUR_USD = { id: 'r1', from: 'EUR', to: 'USD', rate: '1.10', validFrom: '2026-01-01', version: 3 };

describe('ExchangeRateEntryMapper', () => {
  const mapper = new ExchangeRateEntryMapper();

  it('reads a rate with its own id', () => {
    expect(mapper.fromDto(EUR_USD)).toMatchObject({ id: 'r1', from: 'EUR', to: 'USD', rate: '1.10', validFrom: '2026-01-01', version: 3 });
  });

  it('fills what a sparse response leaves out with empty text', () => {
    expect(mapper.fromDto({})).toMatchObject({ id: undefined, from: '', to: '', rate: '', validFrom: '' });
  });

  it('writes the fields one by one, the decimal and the date trimmed, and never the id', () => {
    const dto = mapper.toDto(new ExchangeRateEntry({ id: 'r1', from: 'EUR', to: 'USD', rate: ' 1.10 ', validFrom: ' 2026-01-01 ', version: 3 }));

    expect(dto).toEqual({ from: 'EUR', to: 'USD', rate: '1.10', validFrom: '2026-01-01', version: 3 });
  });
});

describe('the Exchange Rate descriptor', () => {
  const descriptor = createExchangeRateDescriptor(() => ['EUR', 'HUF', 'USD']);
  const attributes = () => {
    const found: BaseEntityAttrDescriptor[] = [];
    const walk = (list: readonly unknown[]) => list.forEach((item) => (item instanceof FlexboxDescriptor ? walk(item.attrDescriptors) : found.push(item as BaseEntityAttrDescriptor)));
    walk(descriptor.attrDescriptors);
    return found;
  };
  const attribute = (name: string) => attributes().find((candidate) => candidate.attrName === name) as BaseEntityAttrDescriptor;

  it('offers the currencies of the catalog for both sides of the pair, read when the dropdown opens', () => {
    const options = (name: string) => {
      const selectables = attribute(name).selectables;
      return typeof selectables === 'function' ? selectables() : selectables;
    };
    const expected = [
      { key: 'EUR', value: 'EUR' },
      { key: 'HUF', value: 'HUF' },
      { key: 'USD', value: 'USD' },
    ];

    expect(options('from')).toEqual(expected);
    expect(options('to')).toEqual(expected);
    expect(attribute('from').formControlType).toBe(FormControlType.DROPDOWN);
  });

  it('needs the pair, the rate and the date, and reads the rate and the date as the backend does', () => {
    expect(['from', 'to', 'rate', 'validFrom'].map((name) => attribute(name).required)).toEqual([true, true, true, true]);
    const decimal = new RegExp(attribute('rate').pattern as string);
    expect(['395.5', '1', '.25', '12.'].every((text) => decimal.test(text))).toBe(true);
    expect(['', '-1', '1e3', 'many'].some((text) => decimal.test(text))).toBe(false);
    const date = new RegExp(attribute('validFrom').pattern as string);
    expect(date.test('2026-07-01')).toBe(true);
    expect(['2026-7-1', 'yesterday', ''].some((text) => date.test(text))).toBe(false);
  });

  it('can be deleted, unlike a unit: nothing stored carries a rate', () => {
    expect(descriptor.isDeletable).toBeUndefined();
  });
});

describe('ExchangeRateAuthoringService', () => {
  let service: ExchangeRateAuthoringService;
  let rates: OrganizationExchangeRates;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: NGXLogger, useValue: { warn: vi.fn() } },
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { ENTITY_SERVICE_ROOT: 'http://localhost:8080/organizations/acme' } } },
      ],
    });
    service = TestBed.inject(ExchangeRateAuthoringService);
    rates = TestBed.inject(OrganizationExchangeRates);
    controller = TestBed.inject(HttpTestingController);
  });

  it('creates a rate and reloads the table, so the next conversion uses it', async () => {
    const created = firstValueFrom(service.add(new ExchangeRateEntry({ from: 'EUR', to: 'USD', rate: '1.10', validFrom: '2026-01-01' })));
    const post = controller.expectOne(RATES_URL);
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toMatchObject({ from: 'EUR', to: 'USD', rate: '1.10', validFrom: '2026-01-01' });
    post.flush(EUR_USD);
    expect((await created).id).toBe('r1');

    controller.expectOne(RELOAD_URL).flush({ content: [EUR_USD] });
    await rates.ensureLoaded();
    expect(rates.rates().rate('EUR', 'USD', '2026-03-01').toString()).toBe('1.1');
  });

  it('replaces a rate by its id with the version it was read at, and reloads the table', async () => {
    const updated = firstValueFrom(service.update(new ExchangeRateEntry({ ...EUR_USD, rate: '1.25' }) as ExchangeRateEntry & { id: string }));
    const put = controller.expectOne(`${RATES_URL}/r1`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toMatchObject({ rate: '1.25', version: 3 });
    put.flush({ ...EUR_USD, rate: '1.25', version: 4 });
    expect((await updated).version).toBe(4);

    controller.expectOne(RELOAD_URL).flush({ content: [{ ...EUR_USD, rate: '1.25' }] });
    await rates.ensureLoaded();
    expect(rates.rates().rate('EUR', 'USD', '2026-03-01').toString()).toBe('1.25');
  });

  it('deletes a rate and reloads the table, which no longer has it', async () => {
    const deleted = firstValueFrom(service.delete('r1'), { defaultValue: null });
    const request = controller.expectOne(`${RATES_URL}/r1`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await deleted;

    controller.expectOne(RELOAD_URL).flush({ content: [] });
    await rates.ensureLoaded();
    expect(rates.rates()).toBe(ExchangeRates.NONE);
  });

  it('does not reload the table when a save fails', async () => {
    const failed = firstValueFrom(service.add(new ExchangeRateEntry({ from: 'EUR', to: 'USD' }))).catch((error: unknown) => error);
    controller.expectOne(RATES_URL).flush({ message: 'conflict' }, { status: 409, statusText: 'Conflict' });
    expect(await failed).toMatchObject({ status: 409 });

    controller.expectNone(RELOAD_URL);
  });
});
