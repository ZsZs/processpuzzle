import { TestBed } from '@angular/core/testing';
import { EntityDefinitionRegistry } from '@processpuzzle/base-entity';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import type { CalculationOutput } from './calculation-api';
import { CalculationApiService } from './calculation-api.service';
import { CalculationOutputRegistry } from './calculation-output.registry';

const TOTAL: CalculationOutput = { calculationId: 'order-total', attribute: 'total', decision: 'total', parameter: 'order' };

describe('CalculationOutputRegistry', () => {
  function setup(outputs: ReturnType<typeof vi.fn>, byName: (name: string) => Promise<{ code: string } | undefined> = async () => undefined) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        { provide: CalculationApiService, useValue: { outputs } },
        { provide: EntityDefinitionRegistry, useValue: { byName: vi.fn(byName) } },
      ],
    });
    return TestBed.inject(CalculationOutputRegistry);
  }

  it("asks by the entity's definition code and caches the answer", async () => {
    const outputs = vi.fn(() => of([TOTAL]));
    const registry = setup(outputs, async () => ({ code: 'sales-order' }));

    expect(await registry.outputsOf('Order')).toEqual([TOTAL]);
    expect(await registry.outputFor('Order', 'total')).toEqual(TOTAL);
    expect(await registry.outputFor('Order', 'name')).toBeUndefined();
    expect(outputs).toHaveBeenCalledTimes(1);
    expect(outputs).toHaveBeenCalledWith('sales-order');
  });

  it('falls back to the snake-cased name for an entity without a definition', async () => {
    const outputs = vi.fn(() => of([]));

    await setup(outputs).outputsOf('Special Order');

    expect(outputs).toHaveBeenCalledWith('special-order');
  });

  it('answers nothing, and asks again next time, when the endpoint fails', async () => {
    const outputs = vi.fn().mockReturnValueOnce(throwError(() => new Error('down'))).mockReturnValueOnce(of([TOTAL]));
    const registry = setup(outputs);

    expect(await registry.outputsOf('Order')).toEqual([]);
    expect(await registry.outputsOf('Order')).toEqual([TOTAL]);
  });

  it('fetches again after a reset', async () => {
    const outputs = vi.fn(() => of([TOTAL]));
    const registry = setup(outputs);

    await registry.outputsOf('Order');
    registry.reset();
    await registry.outputsOf('Order');

    expect(outputs).toHaveBeenCalledTimes(2);
  });

  it('answers nothing for no entity name', async () => {
    const outputs = vi.fn(() => of([TOTAL]));

    expect(await setup(outputs).outputsOf(undefined)).toEqual([]);
    expect(outputs).not.toHaveBeenCalled();
  });
});
