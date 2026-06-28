import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { BaseRule } from './base-rule';
import { BaseRuleService } from './base-rule.service';

export const BaseRuleStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<BaseRule>(BaseRule, () => inject(BaseRuleService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
);
