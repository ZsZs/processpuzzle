import { Injectable } from '@angular/core';
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { BaseRule } from './base-rule';
import { BaseRuleMapper } from './base-rule.mapper';

@Injectable({ providedIn: 'root' })
export class BaseRuleService extends BaseEntityRestService<BaseRule> {
  constructor(protected override entityMapper: BaseRuleMapper) {
    super(entityMapper, 'RULE_SERVICE_ROOT', 'rules');
  }
}
