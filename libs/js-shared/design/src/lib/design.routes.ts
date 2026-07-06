import { Routes } from '@angular/router';
import { BASE_RULE_ROUTES } from '@processpuzzle/base-rule';
import { DesignContentComponent } from './content/design-content.component';

export const DESIGN_ROUTES: Routes = [{ path: '', pathMatch: 'full', component: DesignContentComponent }, ...BASE_RULE_ROUTES];
