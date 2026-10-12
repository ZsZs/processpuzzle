import { HttpContext } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { definitionRead } from '@processpuzzle/util';
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { BaseRule } from './base-rule';
import { BaseRuleMapper } from './base-rule.mapper';

@Injectable({ providedIn: 'root' })
export class BaseRuleService extends BaseEntityRestService<BaseRule> {
  /** Reads of definitions, addressed by the host to the draft or the released revision; see `definitionRevisionInterceptor`. */
  protected override readContext(): HttpContext {
    return definitionRead();
  }

  constructor(protected override entityMapper: BaseRuleMapper) {
    super(entityMapper, 'RULE_SERVICE_ROOT', 'rules');
  }
}
