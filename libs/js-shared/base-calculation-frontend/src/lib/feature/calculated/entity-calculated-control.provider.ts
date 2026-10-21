import { inject, Injectable, Provider } from '@angular/core';
import { provideTranslocoScope } from '@jsverse/transloco';
import { ENTITY_CALCULATED_CONTROL, type BaseEntityDescriptor, type EntityCalculatedControl } from '@processpuzzle/base-entity';
import { BASE_CALCULATION_TRANSLOCO_SCOPE } from '../../base-calculation.i18n';
import { CalculationOutputRegistry } from '../../domain/calculation-output.registry';
import { CalculatedControlComponent } from './calculated-control.component';

/**
 * base-calculation's answer to base-entity's `ENTITY_CALCULATED_CONTROL`: the attributes a released, stored
 * calculation writes, rendered by {@link CalculatedControlComponent}.
 */
@Injectable({ providedIn: 'root' })
export class BaseCalculationEntityCalculatedControl implements EntityCalculatedControl {
  readonly component = CalculatedControlComponent;
  private readonly outputs = inject(CalculationOutputRegistry);

  async calculatedAttributesOf(descriptor: BaseEntityDescriptor): Promise<string[]> {
    return [...new Set((await this.outputs.outputsOf(descriptor.entityName)).map((output) => output.attribute))];
  }
}

/**
 * Registers the CALCULATED form control for the whole application, beside `provideEntityStateControl()`. The
 * transloco scope is registered here too: the control renders on any entity's route, which does not register
 * `base_calculation`.
 */
export function provideEntityCalculatedControl(): Provider[] {
  return [
    { provide: ENTITY_CALCULATED_CONTROL, useExisting: BaseCalculationEntityCalculatedControl },
    provideTranslocoScope({ scope: BASE_CALCULATION_TRANSLOCO_SCOPE, alias: BASE_CALCULATION_TRANSLOCO_SCOPE }),
  ];
}
