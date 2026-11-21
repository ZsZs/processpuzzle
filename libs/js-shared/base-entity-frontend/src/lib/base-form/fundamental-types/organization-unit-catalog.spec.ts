import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION, STANDARD_UNIT_CATALOG } from '@processpuzzle/util';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { quantityUnitsOf } from './measure-options';
import { catalogOf, OrganizationUnitCatalog } from './organization-unit-catalog';
import { OrganizationUnit } from './organization-unit.service';
import { UNIT_CATALOG } from './unit-catalog.token';

const UNITS_URL = 'http://localhost:8080/organizations/acme/units?page=0&size=200';
const PALLET: OrganizationUnit = { code: 'pallet', symbol: 'plt', name: 'Pallet', kind: 'MASS', factor: '500', status: 'ACTIVE' };

describe('the organization’s unit catalog', () => {
  let catalogs: OrganizationUnitCatalog;
  let controller: HttpTestingController;

  function setup(baseConfiguration: object = { ENTITY_SERVICE_ROOT: 'http://localhost:8080/organizations/acme' }) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: baseConfiguration } }],
    });
    catalogs = TestBed.inject(OrganizationUnitCatalog);
    controller = TestBed.inject(HttpTestingController);
  }

  beforeEach(() => setup());

  it('is the standard catalog until the units are loaded', () => {
    expect(catalogs.catalog()).toBe(STANDARD_UNIT_CATALOG);
    expect(TestBed.inject(UNIT_CATALOG).findUnit('pallet')).toBeUndefined();
  });

  it('adds the units it loads, and the catalog every control reads follows without being replaced', async () => {
    const seen = TestBed.inject(UNIT_CATALOG);
    const loaded = catalogs.ensureLoaded();
    controller.expectOne(UNITS_URL).flush({ content: [PALLET] });
    await loaded;

    expect(seen.findUnit('pallet')).toMatchObject({ kind: 'MASS', factor: '500', retired: false });
    expect(seen.unitsOf('MASS').map((unit) => unit.code)).toContain('pallet');
    expect(seen.kinds().find((kind) => kind.code === 'MASS')?.units.map((unit) => unit.code)).toContain('pallet');
    expect(seen.currencies()).toHaveLength(STANDARD_UNIT_CATALOG.currencies().length);
    expect(seen.findCurrency('EUR')?.minorUnits).toBe(2);
    expect(seen.unit('pallet').symbol).toBe('plt');
    expect(seen.kind('MASS').baseUnit).toBe('kg');
    expect(seen.currency('EUR').code).toBe('EUR');
    expect(seen.activeUnitsOf('MASS').map((unit) => unit.code)).toContain('pallet');
    expect(seen.withUnits([]).findUnit('pallet')).toBeDefined();
  });

  it('asks once, however many callers wait, and again after it is invalidated', async () => {
    const first = Promise.all([catalogs.ensureLoaded(), catalogs.ensureLoaded()]);
    controller.expectOne(UNITS_URL).flush({ content: [] });
    await first;

    await catalogs.ensureLoaded();
    controller.expectNone(UNITS_URL);

    catalogs.invalidate();
    const again = catalogs.ensureLoaded();
    controller.expectOne(UNITS_URL).flush({ content: [PALLET] });
    await again;
    expect(catalogs.catalog().findUnit('pallet')).toBeDefined();
  });

  it('keeps a retired unit known but does not offer it', async () => {
    const loaded = catalogs.ensureLoaded();
    controller.expectOne(UNITS_URL).flush({ content: [{ ...PALLET, status: 'RETIRED' }] });
    await loaded;

    const catalog = TestBed.inject(UNIT_CATALOG);
    const weight = new BaseEntityAttrDescriptor('weight', FormControlType.QUANTITY, 'Weight');
    weight.quantityKind = 'MASS';
    expect(catalog.findUnit('pallet')?.retired).toBe(true);
    expect(quantityUnitsOf(weight, catalog).map((unit) => unit.code)).not.toContain('pallet');
    weight.allowedUnits = ['kg', 'pallet'];
    expect(quantityUnitsOf(weight, catalog).map((unit) => unit.code)).toEqual(['kg']);
  });

  it('is the standard catalog where the host serves no units or the call fails', async () => {
    const failed = catalogs.ensureLoaded();
    controller.expectOne(UNITS_URL).flush('nope', { status: 500, statusText: 'Server Error' });
    await failed;
    expect(catalogs.catalog().findUnit('pallet')).toBeUndefined();

    setup({});
    await catalogs.ensureLoaded();
    controller.expectNone(UNITS_URL);
    expect(catalogs.catalog()).toBe(STANDARD_UNIT_CATALOG);
  });

  it('accepts a bare array and ignores a body that is neither', async () => {
    const bare = catalogs.ensureLoaded();
    controller.expectOne(UNITS_URL).flush([PALLET]);
    await bare;
    expect(catalogs.catalog().findUnit('pallet')).toBeDefined();

    setup();
    const odd = catalogs.ensureLoaded();
    controller.expectOne(UNITS_URL).flush('<html></html>');
    await odd;
    expect(catalogs.catalog()).toBe(STANDARD_UNIT_CATALOG);
  });
});

describe('catalogOf', () => {
  it('leaves out a unit the catalog refuses and keeps the others, saying so', () => {
    const logger = { warn: vi.fn() };
    const catalog = catalogOf([{ ...PALLET, code: 'kg' }, { ...PALLET, kind: 'NO_SUCH_KIND', code: 'ghost' }, PALLET], logger);

    expect(catalog.findUnit('ghost')).toBeUndefined();
    expect(catalog.unit('kg').factor).toBe('1');
    expect(catalog.findUnit('pallet')).toBeDefined();
    expect(logger.warn).toHaveBeenCalledTimes(2);
  });

  it('reads a divisor and defaults it to one', () => {
    const catalog = catalogOf([{ ...PALLET, code: 'half', factor: '1', divisor: '2' }]);
    expect(catalog.unit('half').divisor).toBe('2');
    expect(catalogOf([PALLET]).unit('pallet').divisor).toBe('1');
  });
});
