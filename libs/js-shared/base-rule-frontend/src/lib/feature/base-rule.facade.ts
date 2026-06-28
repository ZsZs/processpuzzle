import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { BaseRule } from '../domain/base-rule';
import { BaseRuleMapper } from '../domain/base-rule.mapper';
import { BaseRuleService } from '../domain/base-rule.service';
import { BaseRuleStore } from '../domain/base-rule.store';
import { createBaseRuleDescriptor } from '../domain/base-rule.descriptors';

@Injectable()
export class BaseRuleFacade extends BaseEntityFacade<BaseRule> {
  readonly entityType = BaseRule;

  private readonly mapperRef = inject(BaseRuleMapper);
  private readonly serviceRef = inject(BaseRuleService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return BaseRuleStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createBaseRuleDescriptor();
  }
}
