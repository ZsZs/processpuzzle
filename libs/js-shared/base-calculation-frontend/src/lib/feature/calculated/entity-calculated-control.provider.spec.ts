import { TestBed } from '@angular/core/testing';
import { BaseEntityAttrDescriptor, BaseEntityDescriptor, ENTITY_CALCULATED_CONTROL, FormControlType } from '@processpuzzle/base-entity';
import { describe, expect, it, vi } from 'vitest';
import { CalculationOutputRegistry } from '../../domain/calculation-output.registry';
import { CalculatedControlComponent } from './calculated-control.component';
import { BaseCalculationEntityCalculatedControl, provideEntityCalculatedControl } from './entity-calculated-control.provider';

const descriptor = new BaseEntityDescriptor({ entityName: 'Order', attrDescriptors: [new BaseEntityAttrDescriptor('total', FormControlType.TEXT_BOX, 'Total')] });

describe('BaseCalculationEntityCalculatedControl', () => {
  function setup(outputsOf: () => Promise<unknown[]>) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [{ provide: CalculationOutputRegistry, useValue: { outputsOf: vi.fn(outputsOf) } }, ...provideEntityCalculatedControl()] });
    return TestBed.inject(BaseCalculationEntityCalculatedControl);
  }

  it('names each attribute a calculation writes once', async () => {
    const control = setup(async () => [{ attribute: 'total' }, { attribute: 'discount' }, { attribute: 'total' }]);

    expect(await control.calculatedAttributesOf(descriptor)).toEqual(['total', 'discount']);
  });

  it('names nothing for an entity no calculation writes', async () => {
    expect(await setup(async () => []).calculatedAttributesOf(descriptor)).toEqual([]);
  });

  it('renders the attribute with CalculatedControlComponent', () => {
    expect(setup(async () => []).component).toBe(CalculatedControlComponent);
  });

  it('registers itself as ENTITY_CALCULATED_CONTROL', () => {
    setup(async () => []);

    expect(TestBed.inject(ENTITY_CALCULATED_CONTROL)).toBe(TestBed.inject(BaseCalculationEntityCalculatedControl));
  });
});
