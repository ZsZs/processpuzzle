import { Routes } from '@angular/router';
import { BASE_ENTITY_ROUTES } from '@processpuzzle/base-entity';
import { BaseRuleContainerComponent } from './feature/base-rule-container.component';

export const BASE_RULE_ROUTES: Routes = [
  {
    path: 'base-rule',
    component: BaseRuleContainerComponent,
    children: BASE_ENTITY_ROUTES,
  },
];
