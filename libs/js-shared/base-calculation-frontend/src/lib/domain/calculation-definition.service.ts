import { HttpContext } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { definitionRead } from '@processpuzzle/util';
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { CalculationDefinition } from './calculation-definition';
import { CalculationDefinitionMapper } from './calculation-definition.mapper';

/**
 * REST access to `/organizations/{orgKey}/calculation-definitions`. `CALCULATION_SERVICE_ROOT` is optional and
 * falls back to `BACKEND_SERVICE_ROOT`, like every other feature's root.
 */
@Injectable({ providedIn: 'root' })
export class CalculationDefinitionService extends BaseEntityRestService<CalculationDefinition> {
  /** Reads of definitions, addressed by the host to the draft or the released revision. */
  protected override readContext(): HttpContext {
    return definitionRead();
  }

  constructor(protected override entityMapper: CalculationDefinitionMapper) {
    super(entityMapper, 'CALCULATION_SERVICE_ROOT', 'calculation-definitions');
  }
}
