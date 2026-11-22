import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { FlexboxDescriptor } from '../base-entity/flexboxDescriptor';
import { OrganizationUnitCatalog } from '../base-form/fundamental-types/organization-unit-catalog';
import { OrganizationUnitRecord } from './organization-unit';
import { OrganizationUnitAuthoringService } from './organization-unit-authoring.service';
import { createOrganizationUnitDescriptor } from './organization-unit.descriptors';
import { OrganizationUnitMapper } from './organization-unit.mapper';

const UNITS_URL = 'http://localhost:8080/organizations/acme/units';
const PALLET_DTO = { id: 'f0e1', code: 'pallet', symbol: 'plt', name: 'Pallet', kind: 'MASS', factor: '500', divisor: '1', status: 'ACTIVE', version: 3 };

describe('OrganizationUnitMapper', () => {
  const mapper = new OrganizationUnitMapper();

  it('reads a unit with its code as the record’s id', () => {
    expect(mapper.fromDto(PALLET_DTO)).toMatchObject({ id: 'pallet', code: 'pallet', symbol: 'plt', kind: 'MASS', factor: '500', divisor: '1', status: 'ACTIVE', version: 3 });
  });

  it('fills what a sparse response leaves out with the record’s defaults', () => {
    expect(mapper.fromDto({})).toMatchObject({ id: '', code: '', divisor: '1', status: 'ACTIVE' });
  });

  it('writes the fields one by one, trimmed, and sends a blank divisor as absent', () => {
    const dto = mapper.toDto(new OrganizationUnitRecord({ id: 'pallet', code: 'pallet', symbol: 'plt', name: 'Pallet', kind: 'MASS', factor: ' 500 ', divisor: '  ', status: 'RETIRED', version: 3 }));

    expect(dto).toEqual({ code: 'pallet', symbol: 'plt', name: 'Pallet', kind: 'MASS', factor: '500', divisor: undefined, status: 'RETIRED', version: 3 });
    expect(mapper.toDto(new OrganizationUnitRecord({ code: 'half', factor: '1', divisor: '2' })).divisor).toBe('2');
  });
});

describe('the Organization Unit descriptor', () => {
  const descriptor = createOrganizationUnitDescriptor(['MASS', 'LENGTH']);
  const attributes = () => {
    const found: BaseEntityAttrDescriptor[] = [];
    const walk = (list: readonly unknown[]) =>
      list.forEach((item) => (item instanceof FlexboxDescriptor ? walk(item.attrDescriptors) : found.push(item as BaseEntityAttrDescriptor)));
    walk(descriptor.attrDescriptors);
    return found;
  };
  const attribute = (name: string) => attributes().find((candidate) => candidate.attrName === name) as BaseEntityAttrDescriptor;

  it('offers the standard kinds and the two statuses', () => {
    expect(attribute('kind').selectables).toEqual([{ key: 'MASS', value: 'MASS' }, { key: 'LENGTH', value: 'LENGTH' }]);
    expect(attribute('status').selectables).toEqual([{ key: 'ACTIVE', value: 'ACTIVE' }, { key: 'RETIRED', value: 'RETIRED' }]);
    expect(attribute('status').formControlType).toBe(FormControlType.DROPDOWN);
  });

  /** A stored amount is derived from them, so a saved unit keeps them; only the status moves. */
  it('fixes the code, kind, factor and divisor once saved, and leaves the rest editable', () => {
    expect(['code', 'kind', 'factor', 'divisor'].map((name) => attribute(name).createOnly)).toEqual([true, true, true, true]);
    expect(['symbol', 'name', 'status'].map((name) => attribute(name).createOnly)).toEqual([false, false, false]);
  });

  it('reads the factor and divisor as positive decimals', () => {
    const decimal = new RegExp(attribute('factor').pattern as string);
    expect(['500', '0.5', '12.', '.25'].every((text) => decimal.test(text))).toBe(true);
    expect(['', '0x1', '-1', '1e3', 'many'].some((text) => decimal.test(text))).toBe(false);
    expect(attribute('divisor').pattern).toBe(attribute('factor').pattern);
  });

  it('checks the code the way the backend does', () => {
    const code = new RegExp(attribute('code').pattern as string);
    expect(['pallet', 'big-bag_2'].every((text) => code.test(text))).toBe(true);
    expect(['2pallet', 'a b', ''].some((text) => code.test(text))).toBe(false);
  });

  it('offers no delete: a stored value may carry the unit, so it is retired instead', () => {
    expect(descriptor.isDeletable?.(new OrganizationUnitRecord({ code: 'pallet' }))).toBe(false);
  });
});

describe('OrganizationUnitAuthoringService', () => {
  let service: OrganizationUnitAuthoringService;
  let catalog: OrganizationUnitCatalog;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { ENTITY_SERVICE_ROOT: 'http://localhost:8080/organizations/acme' } } },
      ],
    });
    service = TestBed.inject(OrganizationUnitAuthoringService);
    catalog = TestBed.inject(OrganizationUnitCatalog);
    controller = TestBed.inject(HttpTestingController);
  });

  it('creates a unit and reloads the catalog, so the unit is known at once', async () => {
    const created = firstValueFrom(service.add(new OrganizationUnitRecord({ code: 'pallet', symbol: 'plt', name: 'Pallet', kind: 'MASS', factor: '500' })));
    const post = controller.expectOne(UNITS_URL);
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toMatchObject({ code: 'pallet', kind: 'MASS', factor: '500' });
    post.flush(PALLET_DTO);
    expect((await created).id).toBe('pallet');

    controller.expectOne(`${UNITS_URL}?page=0&size=200`).flush({ content: [PALLET_DTO] });
    await catalog.ensureLoaded();
    expect(catalog.catalog().findUnit('pallet')).toBeDefined();
  });

  it('replaces a unit by its code, with the version it was read at, and reloads the catalog', async () => {
    const updated = firstValueFrom(service.update(new OrganizationUnitRecord({ id: 'pallet', code: 'pallet', symbol: 'plt', name: 'Pallet', kind: 'MASS', factor: '500', status: 'RETIRED', version: 3 }) as OrganizationUnitRecord & { id: string }));
    const put = controller.expectOne(`${UNITS_URL}/pallet`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toMatchObject({ status: 'RETIRED', version: 3 });
    put.flush({ ...PALLET_DTO, status: 'RETIRED', version: 4 });
    expect((await updated).version).toBe(4);

    controller.expectOne(`${UNITS_URL}?page=0&size=200`).flush({ content: [{ ...PALLET_DTO, status: 'RETIRED' }] });
    await catalog.ensureLoaded();
    expect(catalog.catalog().findUnit('pallet')?.retired).toBe(true);
  });

  it('does not reload the catalog when the save fails', async () => {
    const failed = firstValueFrom(service.add(new OrganizationUnitRecord({ code: 'pallet' }))).catch((error: unknown) => error);
    controller.expectOne(UNITS_URL).flush({ message: 'conflict' }, { status: 409, statusText: 'Conflict' });
    expect(await failed).toMatchObject({ status: 409 });

    controller.expectNone(`${UNITS_URL}?page=0&size=200`);
  });
});
